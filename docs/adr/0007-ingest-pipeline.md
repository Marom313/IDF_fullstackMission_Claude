# ADR 0007 – צנרת קליטה ("הלבנה"): שירות Java במקום flow של NiFi
**סטטוס:** מאושר · 20/11/2026 · מחליף חלקית את ADR 0005 (סעיף NiFi)

## הקשר
התכנון המקורי הגדיר את NiFi כמתזמר של צנרת הקליטה. אילוצים:
- זמן הבנייה (יום אחד).
- NiFi צורך כ-1.5GB RAM.
- ל-NiFi אין processor מובנה לאימות Ed25519. היה צורך בסקריפט או ב-processor מותאם.
- כתיבת flow "עיוורת" (בלי UI) מסוכנת.

## חלופות
1. **flow ב-NiFi 2.x:** נאמן לתכנון, יקר וסיכון גבוה.
2. **שירות Java קטן (`ingest-service`) עם אותם 9 שלבים ואותו סדר.** נבחר.
3. **דילוג על קליטה:** נפסל, כי זה מוריד את "סגירת המעגל" מהדמו.

## החלטה
`ingest-service` ב-profile `ingest`, 384MB. מיפוי אחד-לאחד ל-processors של NiFi, כך שהמעבר עתידי:

| שלב | ingest-service | processor ב-NiFi |
|---|---|---|
| 1 | poll ל-`ingest/inbox` (csv + manifest + sig) | `ListFile` / `FetchFile` |
| 2 | שם, סוג וגודל (≤5MB), התאמה ל-manifest | `RouteOnAttribute` |
| 3 | אימות Ed25519 על bytes של ה-manifest | `ExecuteScript` / processor מותאם, או בשער ה-CDR |
| 4 | SHA-256 מול `manifest.sha256` | `CryptographicHashContent` |
| 5 | dedup לפי hash (קיים ב-archive) | `DetectDuplicate` / lookup |
| 6–7 | סכמה v1, נרמול והסרת עמודות לא מוכרות; שורות פסולות לקובץ rejected | `ValidateRecord` / `UpdateRecord` |
| 8 | `crossing.event.recorded.v1`, key=personId | `PublishKafkaRecord` |
| 9 | העברה ל-archive (שם = sha256) ו-`audit.ingest.v1` | `PutFile` ו-`PublishKafka` |

כל דחייה עוברת ל-`ingest/quarantine` עם קובץ `.reason`, נרשמת ב-audit, ומוצגת במסך הניהול.

## השלכות
- **בעד:** עובד מקצה לקצה היום, בדיקות יחידה לכל check, וחוסך כ-1.1GB RAM.
- **נגד:** אין UI של NiFi להדגמה. בפרודקשן הצנרת עוברת ל-NiFi (או נשארת שירות) **מאחורי** diode ו-CDR, וממשיכה לאמת מחדש. ראו GAPS G-04 ו-G-31.
