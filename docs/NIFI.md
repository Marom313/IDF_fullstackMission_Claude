# NiFi – קליטה והלבנה של קבצי מעבר

> כל הנתונים MOCK. נבנה על ידי סוכן הענן ב-branch `cloud/nifi-ingest`. ההחלטה מתועדת ב-ADR 0008.

**הקשר ל-`ingest-service`:** ב-main יש גם `ingest-service` (ADR 0007), שירות Java שמבצע את אותן בדיקות על שלושה קבצים נפרדים (`crossings_*.csv` + `.manifest.json` + `.manifest.sig`). NiFi קורא רק קבצי `*.zip`, ולכן שתי הצנרות יכולות לרוץ יחד על אותה `ingest/inbox` בלי להתנגש. שתיהן סומכות על אותו מפתח ציבורי (`infra/ingest/keys/ingest.pub.pem`) וכותבות לאותם archive ו-quarantine.

## מה זה עושה, במשפט אחד
מערכת מעברים "חיצונית" שולחת קובץ ZIP חתום עם אירועי מעבר. NiFi מוודא שהקובץ אמיתי, שלם ותקין. אם כן, הוא שולח כל שורה כאירוע ל-Kafka (`crossing.event.recorded.v1`). אם לא, הקובץ עובר להסגר (quarantine) עם קובץ סיבה. בשני המקרים נכתבת רשומת audit (`audit.ingest.v1`).

## למה זה חשוב (סגירת המעגל)
enforcement-service מזהה "נכנס ולא יצא" רק מאירועי מעבר. אם קובץ מזויף או פגום נכנס, הוא עלול להטיל סנקציה על אדם חף מפשע, או להסתיר שהייה בלתי חוקית. לכן כל קובץ שנכנס לרשת הסגורה עובר בדיקה לפני שהוא נוגע במערכת.

## מבנה הקובץ (חוזה, schemaVersion 1)
```
crossings-<time>-<id>.zip
├── data.csv        crossingId,nationalId,gate,direction,occurredAt   (UTF-8, בלי מרכאות)
├── manifest.json   {"fileName":"data.csv","sha256":"…","sizeBytes":…,"rowCount":…,"schemaVersion":1,"createdAt":"…","sourceId":"…"}
└── manifest.sig    base64 של חתימת Ed25519 על הבייטים המדויקים של manifest.json
```

## סדר הבדיקות (`libs/ingest-verifier`, `il.ipp.ingest.verifier.BatchVerifier`)
| # | בדיקה | מגן מפני | קוד הסגר |
|---|---|---|---|
| 1 | גודל ZIP ≤ 5MB | DoS | `SIZE_LIMIT` |
| 2 | בדיוק 3 קבצים, בלי נתיבים, כל קובץ ≤ 10MB אחרי פריסה | zip slip, zip bomb, קבצים מוסתרים | `ZIP_SLIP`, `ZIP_BOMB`, `UNEXPECTED_ENTRY`, `MISSING_ENTRY` |
| 3 | חתימת Ed25519 על manifest.json | **אותנטיות**: מי שלח | `BAD_SIGNATURE` |
| 4 | SHA-256 של data.csv = מה שכתוב ב-manifest, וגם הגודל | **שלמות**: אף בייט לא השתנה בדרך | `HASH_MISMATCH`, `SIZE_MISMATCH` |
| 5 | ה-SHA-256 לא נקלט כבר (`/ingest/.receipts`) | replay, קליטה כפולה | `DUPLICATE_BATCH` |
| 6 | UTF-8 תקין, כותרת מדויקת, מספר שורות | קובץ שבור, החלפת סכמה | `BAD_ENCODING`, `BAD_HEADER`, `ROWCOUNT_MISMATCH` |
| 7 | לכל שורה: אדם מוכר, שער מוכר, ENTRY/EXIT, זמן ISO לא בעתיד | שורות זבל | השורה נזרקת ומדווחת (`ROW_n: UNKNOWN_PERSON` וכו') |

**למה חותמים על ה-manifest ולא על ה-CSV?** החתימה מכסה את ה-SHA-256 שבתוכו, ולכן היא מכסה גם את ה-CSV. כך החתימה קטנה, ואפשר לבדוק אותה לפני שקוראים קובץ גדול.

**למה Ed25519?** זו חתימה אסימטרית: לשולח יש מפתח פרטי, ול-NiFi יש רק את המפתח הציבורי. גם מי שפרץ ל-NiFi לא יכול לזייף קובץ. SHA-256 לבדו לא מספיק: מי שמשנה את הקובץ יכול לחשב hash חדש. Ed25519 מובנה ב-JDK מגרסה 15, בלי ספריות.

**למה ה-verifier הוא jar משלנו ולא processors של NiFi?** יש לו בדיקות יחידה (13 טסטים), הוא דטרמיניסטי, ואפס תלויות, כך שאין מה להעביר לרשת הסגורה. NiFi אחראי על התזמור: האזנה לתיקייה, ניסיונות חוזרים, תורים ו-Kafka. בפרודקשן הבדיקות האלה הן שכבה שנייה, אחרי ה-diode וה-CDR של הארגון (GAPS G-04).

## הזרימה ב-NiFi (`infra/nifi/flow/ipp-ingest.flow.json`)
```
ListFile(/ingest/inbox, *.zip, גיל ≥ 5 שניות, primary node)
  → FetchFile (מוחק מה-inbox)
  → ExecuteStreamCommand: java -jar /opt/ipp/ingest-verifier.jar --key … --receipts … --name ${filename}
       ├─ output stream / nonzero status → SplitText(שורה) → EvaluateJsonPath(_topic,_key)
       │      → ReplaceText(מסיר את _topic,_key) → PublishKafka(topic ${ipp.topic}, key ${ipp.key}=personId)
       └─ original → RouteOnAttribute(execution.status == 0)
              ├─ accepted  → PutFile /ingest/archive
              └─ unmatched → PutFile /ingest/quarantine
                           + ReplaceText(${execution.error}) → UpdateAttribute(<name>.reason) → PutFile
```
ה-JSON נוצר על ידי `node scripts/ingest/build-nifi-flow.mjs`. משנים את הסקריפט, לא את ה-JSON.

## הרצה (פעם אחת, כ-10 דקות)
```bash
# 1. קובץ לדוגמה. אם אין עדיין מפתחות, נוצרים:
#    .ingest-keys/ingest.key.pem (פרטי, לא עולה לגיט) + infra/ingest/keys/ingest.pub.pem (ציבורי)
node scripts/ingest/make-nifi-batch.mjs ok

# 2. הוספת NIFI_USERNAME / NIFI_PASSWORD ל-.env (העתקה מ-.env.example), ואז:
docker compose --profile core --profile nifi build nifi
docker compose --profile core --profile nifi up -d nifi     # ~1.5GB, פרופיל נפרד: `make up` לא מעלה אותו

# 3. ייבוא הזרימה (רק בפעם הראשונה; נשמר ב-volume nificonf)
#    https://localhost:8443/nifi  → אזהרת תעודה (self-signed) → התחברות עם NIFI_USERNAME/NIFI_PASSWORD
#    גוררים "Process Group" לקנבס → אייקון ההעלאה → infra/nifi/flow/ipp-ingest.flow.json → Add
#    כניסה לקבוצה → Controller Services → Enable ל-"Kafka (kafka:9092)" → קליק ימני על הקנבס → Start
#    (או במקום שלב 3 כולו: bash scripts/ingest/nifi-import.sh)
```

## תרחישי דמו
```bash
node scripts/ingest/make-nifi-batch.mjs ok              # → archive, 50 אירועים ב-Kafka
node scripts/ingest/make-nifi-batch.mjs tamper          # בייט אחד שונה אחרי החתימה → quarantine: HASH_MISMATCH
node scripts/ingest/make-nifi-batch.mjs bad-signature   # נחתם במפתח זר → quarantine: BAD_SIGNATURE
node scripts/ingest/make-nifi-batch.mjs duplicate       # אותו קובץ פעמיים → השני ב-quarantine: DUPLICATE_BATCH
node scripts/ingest/make-nifi-batch.mjs zip-slip        # ../../etc/cron.d/evil → quarantine: ZIP_SLIP
node scripts/ingest/make-nifi-batch.mjs bad-rows        # 3 שורות פגומות נזרקות, השאר נקלטות
node scripts/ingest/make-nifi-batch.mjs overstay        # worker1 נכנס לפני 3 ימים ולא יצא → enforcement
```
איפה רואים את התוצאה:
- kafka-ui ב-http://localhost:8085, topics `crossing.event.recorded.v1` ו-`audit.ingest.v1`.
- מסך ההסגר באפליקציה: audit-service מציג כל קובץ ב-`ingest/quarantine` יחד עם הסיבה מקובץ ה-`<name>.reason` שלידו, השם ש-NiFi כותב.
- התיקיות `ingest/archive` ו-`ingest/quarantine`.

**בלי NiFi** (אם המחשב עמוס): `bash scripts/ingest/ingest-direct.sh ingest/inbox/<file>.zip`. הסקריפט מריץ את אותו verifier, מפרסם את אותן הודעות ל-Kafka, ומעביר את הקובץ ל-archive או ל-quarantine.

## ⚠️ באגים ידועים שנמנעו (StackOverflow)
1. **NiFi קורא קובץ שעדיין נכתב:** ListFile רואה קובץ חלקי. הפתרון: `make-nifi-batch` כותב לקובץ `.name.part` ועושה rename, וה-File Filter מתעלם מקבצים שמתחילים בנקודה. בנוסף מוגדר `Minimum File Age` של 5 שניות.
2. **Zip bomb:** `ZipEntry.getSize()` הוא מה שה-ZIP *טוען*, ומותר לו לשקר. הפתרון: סופרים את הבייטים בזמן הקריאה ועוצרים בתקרה (`readCapped`). יש טסט עם 11MB של אפסים שנדחסים לכמה KB.
3. **Zip slip (CVE-2018-1002200 והמשפחה שלו):** שם קובץ כמו `../../x` נכתב מחוץ לתיקייה. כאן לא כותבים לדיסק בכלל, וכל שם עם `/`, `\` או `..` נדחה.
4. **השוואת hash עם `equals`:** השוואה כזו דולפת מידע דרך זמן הריצה (timing). הפתרון: `MessageDigest.isEqual`.
5. **BOM בתחילת CSV מ-Excel:** הכותרת לא תואמת בגלל `﻿` בהתחלה. ה-BOM מוסר לפני ההשוואה.
6. **`execution.status` הוא מחרוזת:** ב-RouteOnAttribute משתמשים ב-`${execution.status:equals('0')}`, לא ב-`== 0`.
7. **PublishKafka ב-NiFi 2 עם `Transactions Enabled=true` (ברירת המחדל) מול broker יחיד:** ל-producer טרנזקציוני צריך `transaction.state.log.replication.factor` (ברירת מחדל 3). ב-broker יחיד הטרנזקציה לא מתחילה, וההודעות נתקעות ב-failure. אצלנו הטרנזקציות כבויות, וה-idempotency היא בצד הצרכן (inbox לפי crossingId, `_id` לפי eventId).
8. **"execution.error" ריק:** NiFi שומר רק את ה-stderr. לכן ה-verifier כותב את הסיבה ל-stderr ואת ההודעות ל-stdout, ולעולם לא מערבב ביניהם.

## בדיקה חיה (21/11/2026, Windows + Docker Desktop)
- 13/13 processors במצב VALID ו-RUNNING. ה-flow לא נזקק לשום תיקון.
- `make-nifi-batch.mjs ok`:
  - הקובץ הגיע ל-`ingest/archive`.
  - 51 הודעות ב-Kafka: 50 מעברים ורשומת audit `INGEST_ACCEPTED 50/50`.
  - enforcement שמר 50 מעברים עם `source=NIFI`.
  - lag 0, ואין DLT.
- `make-nifi-batch.mjs tamper`: הקובץ הגיע ל-`ingest/quarantine`, ולצידו `.reason` של `HASH_MISMATCH: manifest … but data.csv is …`.

**ייבוא בלי UI:** `bash scripts/ingest/nifi-import.sh`. הסקריפט מתחבר, מייבא (אם הקבוצה עוד לא קיימת), מפעיל את שירות ה-Kafka ומריץ Start, דרך ה-REST API של NiFi. אפשר להריץ אותו שוב ושוב בלי נזק.

**תוקן בהרצה:** פקודת ה-build צריכה גם `--profile core`. ה-nifi תלוי ב-kafka, וכשמריצים עם `--profile nifi` לבד, compose נכשל ב-"depends on undefined service".
