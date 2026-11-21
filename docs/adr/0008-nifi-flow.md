# ADR 0008 – NiFi חוזר לצנרת הקליטה, לצד ingest-service
**סטטוס:** מאושר · 21/11/2026 · אושר על ידי מרום · משלים את ADR 0007

## הקשר
- **NiFi נדרש בקול הקורא** (React, TS, Spring, Kafka, NiFi, S3, Redis, Postgres, Mongo). ADR 0007 החליף אותו ב-`ingest-service`, ולכן הטכנולוגיה נעלמה מהדמו.
- **שני החסמים שהוזכרו ב-ADR 0007 נפתרו:**
  1. *אין processor ל-Ed25519:* הבדיקות עברו ל-jar קטן, בלי תלויות (`libs/ingest-verifier`), ש-NiFi מריץ דרך `ExecuteStreamCommand`.
  2. *flow "עיוור":* ה-flow נוצר מקוד (`scripts/ingest/build-nifi-flow.mjs`), שמות ה-properties נבדקו מול התיעוד של NiFi 2.9, וה-verifier נבדק בנפרד (13 טסטים, 7 תרחישים מקצה לקצה).

## החלטה
- **NiFi:** flow של NiFi 2.9 בפרופיל משלו, `nifi` (כ-1.5GB). הוא לא עולה ב-`make up`, ומדליקים אותו רק לדמו.
  - קורא `*.zip` מ-`ingest/inbox`: data.csv + manifest.json + manifest.sig.
  - מאמת עם ה-verifier.
  - מפרסם ל-Kafka (`crossing.event.recorded.v1`, `audit.ingest.v1`).
  - מעביר ל-archive או ל-quarantine, עם קובץ `.reason`.
- **ingest-service נשאר** בפרופיל `ingest`, כצנרת הקלה והברירת מחדל על מחשב הפיתוח:
  - קורא שלישיית קבצים נפרדים (`crossings_*.csv` וכו').
  - אין התנגשות בין הצנרות, כי סוגי הקבצים שונים.
- **מפתח משותף:** `infra/ingest/keys/ingest.pub.pem`. אותו שולח מוכר על ידי שתי הצנרות.

## חלופות
1. **רק ingest-service (ADR 0007):** אין NiFi בדמו, למרות שהוא בדרישות.
2. **רק NiFi:** מוחקים את עבודת הסוכן השני, וכל `make up` עולה 1.5GB יותר.
3. **שתיהן, בפרופילים נפרדים.** נבחר.

## השלכות
- **בעד:**
  - הטכנולוגיה מהקול הקורא קיימת ומודגמת ב-UI.
  - הבדיקות עצמן הן קוד Java עם טסטים, לא סקריפט בתוך NiFi.
  - fallback בלי NiFi (`scripts/ingest/ingest-direct.sh`).
- **נגד:**
  - שני מימושים לאותן בדיקות. ההבדלים מתועדים, וב-GAPS יש איחוד עתידי: ingest-service יכול לקרוא ל-`BatchVerifier` מאותה ספרייה.
  - ייבוא ה-flow ב-UI נעשה ידנית, פעם אחת.
  - הזרימה לא הורצה חי בסביבת הענן (GAPS G-35).
