# פרומפט A: IPP "Balanced Bounded Contexts" (גרסה 2, נבחר)

> **מטרה:** לבנות את Israel Permit Program כמערכת מבוזרת של 6 שירותי דומיין, כל אחד בקונטיינר משלו. המערכת חזקה מספיק להדגים סקייל, ופשוטה מספיק כדי לבנות אותה עד הראיון ולהסביר אותה בו.
> אל תתחיל לכתוב קוד לפני שאישרת את התוכנית של שלב 0.

## הקשר עבודה (חובה לקרוא)
- **ריפו:** `https://github.com/Marom313/IDF_fullstackMission_Claude`. יש בו כבר `presentation_pics/` ו-`test.py`. **לא מוחקים ולא משנים אותם.** את `presentation_pics/` מעבירים ל-`docs/presentation/` רק אם המשתמש מאשר.
- **מחשב המשתמש:** Windows 10, 32GB RAM, Docker Desktop על WSL2, ו-VS Code עם Claude Code. **אסור לתקוע את המחשב:**
  - `docs/setup/wslconfig.example` עם `memory=14GB`, `processors=6`, `swap=4GB`, והוראה להעתיק אותו ל-`%UserProfile%\.wslconfig`.
  - לכל שירות ב-compose יש `mem_limit` ו-`cpus`. שירותי Spring: `mem_limit: 512m`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1` (בפיתוח).
  - Kafka: 768MB. Postgres: 512MB. Mongo: 512MB (`--wiredTigerCacheSizeGB 0.25`). Keycloak: 768MB. NiFi: 1.5GB, רק ב-profile `ingest`.
  - יעד לפרופיל `core`: **עד 8GB בסך הכל.**
  - build של תמונות: `COMPOSE_PARALLEL_LIMIT=2`, multi-stage עם BuildKit cache mount ל-`~/.m2` ול-npm.
- **פקודות חוצות מערכת הפעלה** (בכל המסמך, `ipp <cmd>` פירושו `./ipp.ps1 <cmd>` או `make <cmd>`): `ipp.ps1` (PowerShell) ו-`Makefile` (ל-WSL/Git Bash) עם אותן פקודות: `up`, `down`, `seed`, `verify`, `demo`, `logs`, `reset`.
  - `.gitattributes` עם `* text=auto eol=lf` ו-`*.sh text eol=lf`. בלי זה, סקריפטים שנשמרו ב-Windows עם CRLF נשברים בתוך קונטיינר לינוקס.
- **לוח זמנים:** היום יום שישי אחה"צ, והראיון ביום ראשון. בונים ב**קו MVP** (חובה לדמו) ומעליו **Stretch** (רק אם נשאר זמן). אחרי כל שלב המערכת חייבת לעלות ולעבוד: אף פעם לא משאירים אותה שבורה בסוף שלב.
- **רק כלים חינמיים וקוד פתוח.** אין שירותי ענן בתשלום, אין רישיונות.
- **אחסון אובייקטים: SeaweedFS** (Apache 2.0) עם S3 API. **לא MinIO**: מאגר ה-Community שלו הועבר לארכיון ב-2026 ואינו מתוחזק. ההחלטה מתועדת ב-ADR. הקוד משתמש ב-AWS SDK v2 מול endpoint כללי, כך שהחלפת ספק היא שינוי הגדרה בלבד.
- **מסמך פערים:** `docs/GAPS.md` כבר קיים, וממשיכים לעדכן אותו. **כל קיצור דרך, mock או פשרה נרשם שם מיד**, בפורמט הטבלה הקיים (פער, למה, מה עשינו במקום, מה נדרש לפרודקשן).

## ארכיטקטורה

```
Browser (React SPA, MVVM)
   │  http://localhost
[NGINX]  מגיש את ה-SPA, reverse proxy ל-/api, כותרות אבטחה, gzip
   │
[gateway]  Spring Cloud Gateway: JWT מ-Keycloak, rate-limit (Redis), SSE fan-out, correlation-id
   │
   ├── registry-service     Person, Employer, תמונות (SeaweedFS)   → Postgres schema `registry`
   ├── permit-service       בקשות, היתרים, רישיונות, מכסות, PDF    → Postgres schema `permits` + outbox
   ├── policy-service       DSL (ANTLR), גרסאות, סימולציה           → Mongo `policies`
   ├── eligibility-service  read-model לזכאות (CQRS-lite), API מהיר → Redis + Postgres schema `eligibility`
   ├── enforcement-service  זיהוי הפרות, תיקים, סנקציות, ערעורים    → Postgres schema `enforcement`
   ├── audit-service        צורך audit.*.v1, append-only            → Mongo `audit`
   └── mock-state-api       מרשם, ביטחוני, פלילי (mock), עם הזרקת השהיה וכשל

[Kafka KRaft] ── [NiFi]  ingest/inbox → הלבנה (SHA-256, חתימה, סכמה, quarantine) → crossing.event.recorded.v1
[Keycloak] [PgBouncer→Postgres] [Mongo] [Redis] [SeaweedFS S3] [kafka-ui]
```

**הנתיב החם (בדיקה במעבר):**
1. איש המעבר סורק QR או מקליד ID.
2. `eligibility-service` מחפש ב-Redis (cache hit, בערך 2ms).
3. אם לא נמצא (miss): קורא את ה-read-model ב-Postgres ומחזיר את התשובה ל-cache.
4. **אין קריאה סינכרונית לשירותים אחרים בנתיב החם.** ה-read-model נבנה מאירועי `permit.*`, `sanction.*` ו-`person.*`.

## Docker Compose
- `compose.yaml` עם **profiles**:
  - `core`: infra ו-6 השירותים.
  - `ingest`: NiFi.
  - `observability`: Prometheus ו-Grafana.
- לכל שירות: `healthcheck`, ו-`depends_on: condition: service_healthy`.
- שירות `migrate` (Liquibase) עם `service_completed_successfully` לפני כל שירות שתלוי ב-DB.
- **פורטים:** רק NGINX (80), kafka-ui, Keycloak, NiFi ו-Swagger חשופים ל-localhost. כל השאר ברשת פנימית של compose.

## יעדי סקייל ויציבות
- בדיקת זכאות: p95 < 150ms ב-200 RPS (k6). cache hit ratio מעל 90%.
- `docker compose up --scale eligibility-service=3` עובד בלי שינוי קוד.
- נפילה של `mock-state-api` לא מפילה את המערכת: circuit breaker פותח, ומוחזרת תשובה `REFER_TO_CLERK` עם נימוק.

## שלבים: MVP עד יום ראשון
| שלב | מתי | תוצר | קו |
|---|---|---|---|
| 0 | שישי ערב | שלד monorepo, `.gitattributes`, `CLAUDE.md`, `SPEC.md`, `GAPS.md`, ADRs 0001–0006, compose עם infra ו-limits, Keycloak realm עם משתמשי דמו, `ipp.ps1`/`Makefile`, `verify` ירוק | MVP |
| 1 | שישי לילה | registry-service ו-permit-service (ישויות, Liquibase, outbox, OpenAPI, seed); פרונט: shell, login, i18n ב-4 שפות, tokens, מסך פרופיל ומסך בקשה | MVP |
| 2 | שבת בוקר | policy-service (DSL ו-Studio עם simulate) ו-eligibility-service (read-model, Redis, מסך מעבר ירוק/אדום) | MVP |
| 3 | שבת צהריים | enforcement-service ו-NiFi ingest עם הלבנה; audit-service; DLT ו-replay | MVP |
| 4 | שבת ערב | פורטל מעסיק (חיפוש לפי אזור), PDF רישיון SPECIMEN, דשבורד, k6, axe, `DEMO_SCRIPT.md`, ליטוש עיצוב | MVP |
| 5 | אם נשאר זמן | observability profile, Playwright E2E מלא, chaos (הפלת mock-state-api בלייב) | Stretch |
| — | ראשון בוקר | **הקפאת קוד.** רק תיקוני באגים, חזרה על הדמו ועדכון `GAPS.md` | — |

## מבנה ה-repo
```
/apps/web                      React (MVVM)
/services/<name>               Spring Boot לכל שירות
/libs/java-common              outbox, inbox, problem-details, PII masking, tracing
/contracts/openapi /contracts/events
/infra/{postgres,pgbouncer,keycloak,nifi,nginx,grafana}
/ingest/{inbox,quarantine,archive}   (gitignored, עם .gitkeep)
/scripts/{offline,seed,perf}
/docs/{SPEC.md,WORKLOG.md,GAPS.md,CLOSED_NETWORK.md,DEMO_SCRIPT.md,adr/,perf/,setup/}
CLAUDE.md  Makefile  ipp.ps1  compose.yaml  .env.example  .gitattributes
presentation_pics/  test.py      (קיימים, לא נוגעים)
```

---

# חלק משותף: סטנדרטים שחלים על כל המערכת

## 1. תפקיד וגישה
- אתה Senior Full-Stack Engineer וארכיטקט עם יותר מעשר שנות ניסיון בתעשייה הביטחונית. אתה בונה מוצר דיגיטלי שלם, לא תרגיל.
- המשתמש (המפתח שעובד איתך) רוצה **להבין** כל מה שנבנה. כשאתה מכניס מושג, תבנית או הגדרה שלא הוסברו קודם, הוסף הסבר קצר בעברית ב-WORKLOG ובסיכום הצעד.
- בכל סיכום צעד הוסף סעיף **"⚠️ באגים ידועים להיזהר מהם"**: 1–3 בעיות מוכרות (Stack Overflow, GitHub issues) שרלוונטיות למה שנבנה עכשיו, ואיך הקוד כבר מתגונן מפניהן.
- לפני שימוש בספרייה או בגרסה שאתה לא בטוח בהן, בדוק את התיעוד הרשמי. לא מנחשים API.

## 2. הקשר המוצר: Israel Permit Program (IPP)
מערכת לניהול היתרי כניסה, רישיונות תעסוקה, מדיניות (חוקות), בדיקת זכאות וסגירת מעגל סנקציות על שוהים בלתי חוקיים. **כל הנתונים הם MOCK.** אין להכניס נתון אמיתי של אדם, ארגון או מערכת.

### מונחי ליבה (Ubiquitous Language)
| מונח | משמעות במערכת |
|---|---|
| **Person (פרופיל)** | אדם במערכת: ת"ז/מספר זיהוי, תמונה, כרטיס מגנטי (mock), סטטוס ביטחוני (mock), היסטוריה |
| **Permit (היתר)** | אישור **ספציפי ומוגבל**: מטרה אחת, תוקף קצר, לרוב מעבר/אזור מוגדר. למשל היתר כניסה לעבודה, היתר נהג הובלה, היתר רפואי |
| **Employment License (רישיון תעסוקה)** | אישור **רחב** שניתן למעסיק במסגרת **מכסה לפי ענף**. מתחתיו משובצים עובדים, ולכל עובד משובץ נגזר היתר עבודה |
| **Policy / Rule (מדיניות / חוקה)** | הגדרה מגורסת (versioned) שקובעת אילו שדות חובה, מי זכאי, לכמה זמן ובאילו תנאים. נכתבת ב-DSL ונשמרת ב-Mongo |
| **Eligibility Check (בדיקת זכאות)** | שאילתה מהירה: "האם אדם X רשאי להיכנס או לעבוד עכשיו, ולמה" |
| **Crossing Event (אירוע מעבר)** | רישום כניסה או יציאה במעבר (mock, נקלט דרך NiFi) |
| **Violation / Case / Sanction** | הפרה שזוהתה (למשל נכנס ולא יצא), התיק שנפתח, והסנקציה שהוטלה (השעיה, מניעת כניסה לתקופה) |
| **Closing the loop (סגירת מעגל)** | הפרה, תיק, סנקציה, הודעה, ערעור וסגירה, כשכל שלב מתועד ב-audit |

### משתמשים ותפקידים (RBAC)
- `WORKER`: פועל או מבקש. רואה את הפרופיל שלו, מגיש בקשה, רואה ומוריד את ההיתר שלו (עם QR).
- `DRIVER`: נהג הובלות מחוץ לישראל לתוכה. מגיש בקשת היתר נהג/משאית, כולל פרטי רכב ומטען.
- `EMPLOYER`: מעסיק. מבקש רישיון תעסוקה, משבץ עובדים, **מחפש לפי מיקום או אזור את כל העובדים המאושרים באזור** (ABAC: רק העובדים שלו), ובודק לפי ID אם עובד מאושר.
- `CROSSING_OFFICER`: איש ביטחון במעבר. סורק QR או מקליד ID ומקבל תוך פחות משנייה **ירוק/אדום + תמונה + סיבה**, ומאמת שצילום ההיתר שהוצג תואם לרשומה (בדיקת חתימה דיגיטלית של ה-QR).
- `CLERK`: פקיד. מטפל בבקשות, מאשר או דוחה, מבקש מסמכים.
- `POLICY_ADMIN`: כותב, מסמלץ ומפרסם חוקות.
- `ENFORCEMENT_OFFICER`: מנהל תיקי הפרות וסנקציות.
- `AUDITOR` / `SYS_ADMIN`: צפייה ב-audit, ניהול משתמשים.

## 3. היקף פונקציונלי (חובה)
1. **פרופיל אדם**: תמונה (SeaweedFS (S3), נוצרת אוטומטית כצללית או אווטאר גנרי, **לא תמונות של אנשים אמיתיים**), ID, כרטיס מגנטי, סטטוס, היתרים, הפרות, ציר זמן.
2. **בקשות והיתרים**: טפסים דינמיים שהשדות שלהם **נגזרים מהמדיניות הפעילה** (מה חובה ומה לא). מכונת מצבים: `DRAFT → SUBMITTED → IN_REVIEW → APPROVED | REJECTED → ACTIVE → EXPIRED | SUSPENDED | REVOKED`.
3. **רישיונות תעסוקה**: מכסות לפי ענף (בנייה, חקלאות, תעשייה, שירותים), שיבוץ עובדים ומעקב ניצול מכסה. **מסמך רישיון לדוגמה (PDF)** שמופק מהמערכת עם QR חתום, ועם סימן מים קבוע **"SPECIMEN / דוגמה – אינו בתוקף"**.
4. **Policy Studio**: עורך DSL עם הדגשת תחביר וולידציה חיה, **סימולציה** ("איך החוקה החדשה הייתה מחליטה על 1,000 הבקשות האחרונות"), גרסאות, תאריך תחילה, מצב טיוטה או פעיל, ו-diff בין גרסאות.
5. **בדיקת זכאות**: API ומסך. כניסה לארץ, יציאה לחו"ל, ותעסוקה. התשובה כוללת `decision`, `reasons[]`, `policyVersion` ו-`checkedAt`.
6. **Mock State API**: שירות דמה שמחקה ממשקים ממשלתיים (מרשם, סטטוס ביטחוני, רישום פלילי, מעברים) עם נתונים מזויפים מוגדרים מראש, **השהיה וכשלים מוזרקים** כדי להדגים circuit breaker ו-fallback.
7. **אכיפה וסגירת מעגל**: NiFi קולט אירועי מעבר ושולח אותם ל-Kafka. מנוע זיהוי מוצא "נכנס ולא יצא עד תום תוקף ההיתר", "שהייה ללא היתר" ו"עבודה מחוץ לאזור". נפתח תיק, מוצעת סנקציה לפי מדיניות, ואחריהם הודעה, ערעור וסגירה.
8. **Audit מלא**: כל החלטה (אוטומטית או אנושית) נרשמת: מי, מתי, מה, איזו גרסת מדיניות ומאיזו כתובת. append-only.
9. **דשבורד**: בקשות לפי סטטוס, ניצול מכסות, הפרות פתוחות וזמני תגובה של בדיקת זכאות.
10. **Seed data**: לפחות 5,000 אנשים, 200 מעסיקים, 8,000 היתרים ו-50,000 אירועי מעבר, נוצרים על ידי Data Faker עם seed קבוע (תוצאה זהה בכל הרצה).

### ה-DSL של החוקות
- DSL פירושו Domain-Specific Language: שפה קטנה וייעודית לתחום. היא מאפשרת למנהל מדיניות לכתוב חוקה בלי לשנות קוד ובלי deploy.
- דוגמה לתחביר:
```
RULE "work-permit-construction" VERSION 3 EFFECTIVE 2026-12-01
APPLIES TO permit.type == WORK_ENTRY
REQUIRE FIELDS idNumber, photo, magneticCard, employerId, workRegion
WHEN person.age >= 22
 AND person.securityStatus == CLEAR
 AND employer.sector == CONSTRUCTION
 AND quota("CONSTRUCTION").remaining > 0
 AND NOT sanctions.activeFor(person)
THEN APPROVE VALIDITY 90d
ELSE REFER_TO_CLERK REASON "תנאי זכאות לא מתקיימים"
```
- מימוש: דקדוק **ANTLR4**. הטקסט מתורגם ל-**AST בפורמט JSON** שנשמר ב-Mongo יחד עם הטקסט המקורי וה-hash. המנוע מפרש את ה-AST ב-Java טהור.
- **אסור** להריץ ביטויים דינמיים (SpEL, JavaScript, Groovy): זו דלת להזרקת קוד.
- כל הערכה מחזירה עץ נימוקים (explainability), כדי שהפקיד יבין *למה* התקבלה ההחלטה.

## 4. אילוץ על: רשת סגורה (Air-Gapped)
המערכת חייבת לעבוד **בלי אינטרנט בכלל**:
- **אין CDN.** כל הפונטים (Heebo, Noto Sans Arabic, Noto Sans בשביל קירילית, כולם ברישיון OFL), האייקונים והספריות נארזים מקומית.
- **תמונות Docker** מוצמדות ל-digest או לגרסה מדויקת. אסור `latest`.
- `scripts/offline/bundle.sh` מייצר חבילה שאפשר להעביר לרשת הסגורה: `docker save` לכל התמונות, `mvn dependency:go-offline` לריפו Maven מקומי, ו-cache של npm (`npm ci --cache`). `scripts/offline/load.sh` טוען את החבילה בצד השני.
- כתובות הרג'יסטרים מוגדרות דרך משתני סביבה: `NPM_REGISTRY`, `MAVEN_MIRROR_URL`, `DOCKER_REGISTRY`. ברירת מחדל: ציבורי. ברשת הסגורה: Nexus או Artifactory פנימי.
- תמיכה ב-**CA פנימי** (תעודות ארגוניות) דרך volume של truststore.
- **אף שירות לא פונה החוצה בזמן ריצה.** כל ממשק חיצוני עובר דרך Mock State API, ובפרודקשן מוחלף ב-adapter אמיתי (Ports & Adapters).
- **קליטת מידע מבחוץ ("הלבנה")**: NiFi הוא **מתזמר צנרת הקליטה**, לא מנוע ההלבנה עצמו. בפרודקשן ההלבנה מתבצעת על ידי diode ופתרון CDR מאושר של הארגון. אצלנו NiFi מדמה את הבדיקות ומתעד אותן.
  - **פורמט חבילה נכנסת:** כל קובץ נתונים (`crossings_YYYYMMDD_HHMM.csv`) מגיע עם `manifest.json` שמכיל:
    - `fileName`, `sizeBytes`, `rowCount`, `schemaVersion`, `createdAt`, `sourceId`.
    - `sha256`: hash של הקובץ ב-SHA-256.
    
    ועם `manifest.sig`: חתימת **Ed25519** על ה-manifest. מפתח ציבורי דמו נמצא ב-`infra/nifi/keys/` (המפתח הפרטי לא נכנס ל-git; סקריפט `scripts/ingest/make-batch` יוצר חבילות דמו חתומות).
  - **הצנרת ב-NiFi, לפי הסדר:**
    1. `ListFile`/`FetchFile` מ-`ingest/inbox`.
    2. בדיקת גודל וסוג. חריגה מהמותר עוברת ל-quarantine.
    3. **אימות חתימה** על ה-manifest. כשל עובר ל-quarantine.
    4. **`CryptographicHashContent` (SHA-256)** על הקובץ, והשוואה ל-`sha256` שב-manifest. אי-התאמה עוברת ל-quarantine.
    5. בדיקה מול טבלת `ingest_receipts` שה-hash לא נקלט כבר (מניעת קליטה כפולה).
    6. `ValidateRecord` מול סכמת Avro/JSON. שורות לא תקינות עוברות ל-`rejected` עם סיבה.
    7. נרמול והסרת שדות לא מוכרים.
    8. `PublishKafkaRecord` ל-`crossing.event.recorded.v1`, עם key של `personId`.
    9. העברת הקובץ ל-`ingest/archive`, ואירוע `audit.ingest.v1` שכולל hash, תוצאה וספירות.
  - כל דחייה נרשמת עם סיבה. מסך admin מציג את ה-quarantine.
  - תעד ב-`docs/CLOSED_NETWORK.md` וב-`docs/GAPS.md` שבפרודקשן שלבים 2–4 מתבצעים בשער ההלבנה הארגוני (diode ו-CDR), ו-NiFi רק מאמת שוב.
- SBOM (CycloneDX) לבק ולפרונט, וסריקת פגיעויות (Trivy) שאפשר להריץ offline עם DB מקומי.

## 5. Frontend
- **React + TypeScript (strict) + Vite.**
- **ארכיטקטורת MVVM** לכל feature:
  ```
  src/features/<feature>/
    model/       # טיפוסים (נוצרים מ-OpenAPI), schemas של Zod, API client (TanStack Query hooks)
    viewmodel/   # use<Feature>ViewModel.ts: כל ה-state, הלוגיקה, ההרשאות והפורמט לתצוגה. אין JSX
    view/        # קומפוננטות "טיפשות": מקבלות props מה-ViewModel ומציירות. אין fetch ואין לוגיקה עסקית
    __tests__/   # בדיקות ViewModel עם renderHook, ובדיקות View עם Testing Library
  ```
  כלל ArchUnit-style (eslint-plugin-boundaries): `view` לא מייבא מ-`model` ישירות, רק דרך ה-ViewModel.
- **טפסים**: React Hook Form (uncontrolled) + Zod. ה-ViewModel מחזיק את ה-form instance. בטופס עם 50 שדות, הקלדה בשדה אחד לא מרנדרת את שאר השדות.
- **מידע מהשרת**: TanStack Query:
  - `staleTime` מוגדר לכל סוג מידע.
  - `invalidateQueries` אחרי כל mutation.
  - refetch בחזרה לחלון.
  - בנוסף, ערוץ SSE מה-gateway שמודיע על שינוי סטטוס היתר ומבטל את ה-query הרלוונטי (מידע מיושן לא נשאר על המסך).
- **טבלאות גדולות**: TanStack Table + TanStack Virtual, server-side pagination, מיון וסינון, keyset pagination בבק (לא OFFSET).
- **טיפוסים**: OpenAPI-first. `npm run gen:api` מייצר טיפוסים ו-client (orval) מקבצי ה-spec של השירותים. אסור לכתוב DTO ידנית בפרונט.
- **SCSS**:
  - design tokens כמשתני CSS (צבעים, ריווח, טיפוגרפיה, light/dark).
  - SCSS Modules לכל קומפוננטה.
  - mixins ל-breakpoints ול-focus-ring.
  - **רק CSS Logical Properties** (`margin-inline-start` ולא `margin-left`). stylelint חוסם `left`/`right` פיזיים.
- **i18n**: react-i18next עם 4 שפות: עברית (ברירת מחדל), English, Русский, العربية.
  - בורר שפה בכותרת. הבחירה נשמרת.
  - בהחלפת שפה מתעדכנים `<html lang dir>` (rtl לעברית ולערבית, ltr לאנגלית ולרוסית).
  - תאריכים ומספרים דרך `Intl` לפי locale.
  - שעון: השרת שומר ומחזיר UTC (ISO-8601). הפרונט מציג ב-`Asia/Jerusalem`.
  - טקסט מעורב (ת"ז, לוחית רישוי בתוך משפט בעברית) עטוף ב-`<bdi>`.
  - בדיקת CI שמוודאת שלכל מפתח תרגום יש ערך בכל 4 השפות.
- **עיצוב**:
  - זהות חזותית **מקורית** ל-IPP: פלטה של כחול כהה, זית וחול מדבר, ולוגו SVG מקורי (למשל שער עם מגן מופשט).
  - **אסור להשתמש בסמלים רשמיים** (סמל המדינה, סמל צה"ל, סמל רשות האוכלוסין או המתפ"ש). אם יוחלט בעתיד, לוגו רשמי ייכנס רק כקובץ שהארגון מספק, דרך slot ייעודי `brand/official-logo.svg`.
  - מראה של מוצר ממשלתי-ביטחוני מודרני: נקי, צפוף מידע, קריא, עם מצב כהה. מסך המעבר מותאם לטאבלט, עם כפתורים גדולים וניגודיות גבוהה.
- **נגישות**: יעד **WCAG 2.1 AA**, שמכסה את ת"י 5568:
  - ניווט מלא במקלדת ו-skip link.
  - aria נכון וניגודיות 4.5:1.
  - focus גלוי ו-`prefers-reduced-motion`.
  - כותרות היררכיות ו-labels לכל שדה.
  - הודעות שגיאה מקושרות לשדה (`aria-describedby`).
  - **עמוד הצהרת נגישות**.
  - בדיקות אוטומטיות: axe ב-Vitest, וגם ב-Playwright.

## 6. Backend
- **Java 21, Spring Boot 3.x (הגרסה היציבה העדכנית), Maven multi-module.**
- מבנה פנימי לכל שירות: **Hexagonal / Ports & Adapters** (`domain`, `application`, `adapters/in/web`, `adapters/out/persistence`, `adapters/out/messaging`). ArchUnit אוכף שה-domain לא תלוי ב-Spring.
- **OpenAPI-first**: קובץ `openapi.yaml` לכל שירות. springdoc חושף Swagger UI ב-localhost בלבד.
- **Virtual threads** פעילים (`spring.threads.virtual.enabled=true`). מסבירים ב-WORKLOG מה זה ולמה זה עוזר.
- **שגיאות**: RFC 7807 Problem Details, עם קוד שגיאה עסקי ו-traceId.
- **Resilience4j**: circuit breaker, timeout ו-retry עם jitter על כל קריאה ל-Mock State API.
- **ולידציה**: Bean Validation בכניסה, ו-invariants בתוך ה-domain.
- **Idempotency-Key** header על כל POST שיוצר משאב.
- **Observability**: Actuator, Micrometer ו-Prometheus. לוגים בפורמט JSON עם traceId, ו-**מיסוך PII בלוגים** (ת"ז, טלפון).

## 7. נתונים
### PostgreSQL (מקור האמת ל-Person, Permit, License, Case, Audit-index)
- **Liquibase** לכל שירות: `db/changelog/db.changelog-master.yaml`, changeset קטן לכל שינוי.
  - **אסור לערוך changeset שכבר רץ.** עריכה כזו גורמת ל-checksum error. כל תיקון הוא changeset חדש.
  - rollback מוגדר לכל changeset.
- **הגדרות שרת**: `infra/postgres/postgresql.conf`. רק פרמטרים ששונו מברירת המחדל מופיעים בקובץ.
  - **כלל חובה:** מעל כל פרמטר ששונה מופיעה הערה בפורמט הבא, כדי שכל מי שקורא את הקובץ יבין שזה שינוי מכוון של המפתח:
    ```
    # [MANUAL CHANGE – Marom] max_connections
    # ברירת מחדל: 100 → ערך חדש: 200
    # למה: PgBouncer מרכז את החיבורים; 200 משאיר מקום לשירותים, ל-migrate ולכלי ניהול
    max_connections = 200
    ```
  - הערכים (ברירת המחדל של Postgres בסוגריים):
    - `max_connections = 200` (100)
    - `superuser_reserved_connections = 3` (3, לא משתנה; מתועד בלבד)
    - `statement_timeout = 30s` (0 = ללא הגבלה). ברמת role לשירותי web: `5s`
    - `lock_timeout = 5s` (0)
    - `idle_in_transaction_session_timeout = 60s` (0)
    - `idle_session_timeout = 10min` (0), רק ב-`ALTER ROLE` לתפקידים אינטראקטיביים, לא גלובלי, כדי לא לנתק את PgBouncer
    - `shared_buffers = 256MB` (128MB)
    - `work_mem = 8MB` (4MB)
    - `effective_cache_size = 1GB` (4GB; מוקטן כי הקונטיינר מוגבל ל-512MB)
    - `log_min_duration_statement = 500ms` (-1 = כבוי)
    - `shared_preload_libraries = 'pg_stat_statements'` (ריק)
  - **אותו כלל הערה חל גם על** `pgbouncer.ini`, ועל הגדרות HikariCP ב-`application.yaml`.
  - טבלה מרוכזת של כל השינויים נמצאת ב-`docs/adr/0004-postgres-tuning.md`.
- **PgBouncer** במצב `transaction` מול Postgres. HikariCP: `maximumPoolSize` מחושב (מתועד ב-ADR), `connectionTimeout = 3s`, `leakDetectionThreshold = 20s`.
- **ב-JPA**:
  - `spring.jpa.open-in-view=false`.
  - כל association מוגדר `LAZY`.
  - fetch join או EntityGraph במקום N+1.
  - `@Version` לנעילה אופטימית.
  - DTO projections לרשימות.
  - בדיקת מספר שאילתות בטסטים (datasource-proxy).
- **אינדקסים** לכל שאילתה חמה (מוצדקים עם `EXPLAIN ANALYZE` ב-WORKLOG). טבלת `crossing_events` עם **partitioning חודשי**.
- **user נפרד לכל שירות** עם הרשאות מינימליות. schema נפרד לכל שירות: אף שירות לא קורא schema של שירות אחר.

### MongoDB
- מסמכי מדיניות (טקסט DSL, AST ו-hash), טפסי בקשה גמישים, ו-raw ingest. אינדקסים מוגדרים בקוד (מיגרציה עם Mongock).

### Redis
- **TTL** (Time To Live: זמן אחרי ש-Redis מוחק את המפתח מעצמו) על **כל** מפתח, בלי יוצא מן הכלל.
- read-model לזכאות `elig:{personId}` עם TTL של 10 דקות + jitter. מתבטל (DEL) על ידי אירוע Kafka כשהיתר או סנקציה משתנים.
- הגנה מ-stampede (נעילה קצרה, single-flight).
- rate limiting ב-gateway.
- כל מפתח מתחיל ב-prefix עם גרסה (`v1:`) כדי שאפשר יהיה לבטל cache שלם ב-deploy.

### SeaweedFS (S3)
- תמונות פרופיל, מסמכים מצורפים ו-PDF של היתרים. bucket נפרד לכל סוג. presigned URLs עם תוקף קצר.

### One Source of Truth
- לכל ישות יש בעלים אחד. ADR מפרט "מי הבעלים של מה".
- עותקים בשירותים אחרים הם **read models** שמתעדכנים מאירועים בלבד.

## 8. Kafka
- KRaft (בלי ZooKeeper).
- שמות topics: `<domain>.<entity>.<event>.v1`, למשל `permits.permit.approved.v1`.
- key = `personId`, כדי לשמור סדר אירועים לכל אדם.
- **Transactional Outbox**: הכתיבה לטבלת `outbox` נעשית באותה טרנזקציה של הנתון העסקי. relay מפרסם ל-Kafka.
- **Idempotent consumer**: טבלת `inbox` (`message_id`, `consumer`) עם `ON CONFLICT DO NOTHING`, באותה טרנזקציה של הטיפול.
- **Poison pills**:
  - `ErrorHandlingDeserializer`.
  - `DefaultErrorHandler` עם backoff אקספוננציאלי (3 ניסיונות).
  - `DeadLetterPublishingRecoverer` ל-`<topic>.DLT`.
  - מסך admin להצגת ה-DLT ולשליחה חוזרת (replay).
- סכמות אירועים כ-JSON Schema בתיקייה `contracts/events/`, עם בדיקות תאימות בטסטים.
- `kafka-ui` ב-compose לצפייה בזמן הדמו.

## 9. אבטחה ופרטיות
- **Keycloak** (רץ offline ב-compose) עם OIDC ו-PKCE לפרונט. ה-gateway מאמת JWT. role לכל תפקיד, ו-realm export ב-repo עם משתמשי דמו לכל תפקיד.
- ABAC: מעסיק רואה רק את העובדים שלו. איש מעבר רואה רק נתוני מעבר רלוונטיים.
- QR בהיתר חתום ב-**Ed25519**. המסוף מאמת את החתימה offline, ואז מול השרת.
- כותרות אבטחה ב-NGINX: CSP קשיח, HSTS, X-Frame-Options, Referrer-Policy.
- אין סודות ב-git: רק `.env.example`. ה-repo ציבורי.
- OWASP Top 10: בדיקות ל-IDOR, injection ו-rate limit.

## 10. בדיקות ושערי איכות
- **Backend**: JUnit 5, Testcontainers (Postgres, Kafka, Mongo, Redis אמיתיים), ArchUnit, ובדיקות חוזה מול OpenAPI.
- **Frontend**: Vitest, Testing Library ו-axe. Playwright E2E לזרימה מלאה של כל תפקיד.
- **עומסים**: k6 על בדיקת הזכאות. מטרה: **p95 < 150ms ב-200 בקשות לשנייה** על מחשב פיתוח (פי מאות מ-1,000 היתרים ביום). התוצאות נשמרות ב-`docs/perf/`.
- `ipp verify` מריץ lint, טסטים, build ו-SBOM. אסור commit שבו `ipp verify` אדום.

## 11. קבצי תיעוד שאתה יוצר ומתחזק
- **`CLAUDE.md`** (בשורש): קובץ ההגדרות שלך לסשנים הבאים. כולל:
  - תקציר המוצר ומונחים.
  - מפת ה-repo.
  - פקודות (`ipp up`, `ipp verify`, `ipp seed`, `ipp demo`).
  - כללי קוד (שמות, שכבות, MVVM, Hexagonal).
  - "אסור" (latest images, CDN, עריכת changeset, eval).
  - Definition of Done.
  - נוהל commit.
  
  עד 200 שורות. מתעדכן כשנקבעת החלטה חדשה.
- **`docs/SPEC.md`**: האפיון המלא. מטרות, תפקידים, user stories עם acceptance criteria, ישויות ו-ERD (Mermaid), מכונות מצבים, APIs, אירועים, דרישות לא-פונקציונליות, ו**פרק ייעודי לרשת סגורה**: תלויות, העברת חבילות, הלבנת מידע נכנס, CA פנימי, NTP, והיעדר קריאות יוצאות.
- **`docs/WORKLOG.md`**: יומן עבודה כרונולוגי. לכל צעד:
  - תאריך ושעה.
  - מה נעשה.
  - למה.
  - קבצים עיקריים.
  - פקודות שהורצו ותוצאות build/test.
  - מושגים שהוסברו.
  - באגים ידועים שנמנעו.
  - commit hash.
  
  זה המקום שבו המפתח לומד את המערכת.
- **`docs/adr/NNNN-*.md`**: Architecture Decision Record לכל החלטה (הקשר, חלופות, החלטה, השלכות).
- **`docs/CLOSED_NETWORK.md`**: מדריך העברה לרשת סגורה, צעד אחר צעד.
- **`docs/DEMO_SCRIPT.md`**: תסריט הצגה של 10 דקות לראיון. אילו משתמשים, אילו מסכים, ואיזה "רגע וואו" בכל שלב (סימולציית חוקה, בדיקה במעבר מתחת לשנייה, הפרה שנסגרת אוטומטית, replay מ-DLT).
- **`docs/GAPS.md`**: מסמך הפערים שיוצג למראיין. כל mock, קיצור דרך או פשרה נרשם בו **באותו commit** שבו נוצר הפער. מבנה כל שורה: מזהה, פער, למה, מה עשינו במקום, מה נדרש לפרודקשן, ומאמץ משוער. המסמך מנוסח בטון מקצועי ובטוח: פער מתועד הוא סימן לבגרות הנדסית, לא חולשה.

## 12. Git
- ה-repo ציבורי. עובדים על `main` דרך feature branches קצרים (`feat/...`, `fix/...`) שממוזגים אוטומטית אחרי `verify` ירוק. tag לכל אבן דרך (`v0.1.0`...). בסוף כל שלב, `main` חייב לעלות ולעבוד.
- **Conventional Commits**, באנגלית: `feat(permit): add state machine`.
- **אתה מבצע commit ו-push בעצמך, בלי לבקש אישור**, אחרי כל יחידת עבודה ירוקה.
- לעולם אין commit של סודות, `.env`, קבצי build או נתונים אמיתיים. `.gitignore` ו-pre-commit hook (gitleaks) כבר בצעד הראשון.
- GitHub Actions: build, test ו-SBOM. אותו `ipp verify` רץ גם offline.

## 13. נוהל עבודה בכל צעד
1. הצג תוכנית קצרה לצעד (מטרה, קבצים, סיכונים).
2. ממש כולל טסטים.
3. הרץ `ipp verify` בעצמך ותקן עד ירוק.
4. עדכן את WORKLOG (ואת SPEC, ADR, CLAUDE.md ו-GAPS אם צריך).
5. commit ו-push.
6. סכם למשתמש ב-5–10 שורות: מה נבנה, מה להסתכל עליו, מושג אחד שהוסבר, ו"⚠️ באגים ידועים".
