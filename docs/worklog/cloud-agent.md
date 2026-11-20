# WORKLOG: סוכן הענן (branch `cloud/policy-and-mock`)

> קובץ נפרד מ-`docs/WORKLOG.md` כדי לא ליצור התנגשויות merge עם הסוכן ב-VS Code. במיזוג אפשר להעתיק את הרשומות ל-WORKLOG הראשי.

## חלוקת העבודה
| סוכן | אחריות | branch |
|---|---|---|
| Claude ב-VS Code | שלד, infra, compose, gateway, registry, permit, eligibility, enforcement, audit, פרונט | `main` |
| Claude בענן | `services/policy-service`, `services/mock-state-api` | `cloud/policy-and-mock` |

---

## 2026-11-20 15:05–15:25 · policy-service
**מה נעשה:** מנוע חוקות מלא:
- דקדוק ANTLR, AST ו-parser.
- צורה קנונית ו-diff.
- codec ל-Mongo.
- validator עם רשימה סגורה של פונקציות ומקורות נתונים.
- evaluator תלת-ערכי עם עץ נימוקים.
- סימולציה.
- גרסאות, פרסום ו-retire.
- REST ו-Mongo.

**למה כך:**
- **AST משלנו ולא עץ ANTLR:** ה-domain לא תלוי במחלקות שנוצרות אוטומטית, ואפשר לשמור את העץ ב-Mongo כמסמך קריא.
- **צורה קנונית:** diff בין גרסאות מראה רק שינויים אמיתיים ולא רווחים. ה-SHA-256 מחושב עליה, כך שה-audit יכול להוכיח בדיוק איזו חוקה החליטה.
- **לוגיקה תלת-ערכית (TRUE/FALSE/UNKNOWN):** כשחסר נתון (למשל המרשם נפל), המערכת לא מאשרת אוטומטית ומפנה לפקיד. כלל אצבע במערכות ביטחוניות: fail-safe ולא fail-open.
- **Deny-overrides:** אם כמה חוקות חלות, ההחלטה המחמירה גוברת.
- **snapshot בזיכרון של החוקות הפעילות:** הערכה לא נוגעת ב-Mongo, כמה מיקרו-שניות לבקשה.

**מושגים שהוסברו:**
- **AST (עץ תחביר מופשט):** הייצוג של החוקה כעץ של צמתים (AND, השוואה, נתיב נתון) במקום טקסט. המנוע "הולך" על העץ ומחשב.
- **Kleene logic:** FALSE AND UNKNOWN = FALSE (אין צורך לדעת את שאר הנתונים כדי לדחות), אבל TRUE AND UNKNOWN = UNKNOWN.

**בדיקות:**
- 31 טסטים עברו (parser, evaluator, service).
- ה-parser נוצר עם ANTLR 4.13.2 וקומפל עם javac.
- לא הורצו בענן: שכבת Spring ו-integration tests, כי הגישה ל-Maven Central חסומה בסביבת הענן.

**⚠️ באגים ידועים שנמנעו:**
1. **ANTLR "version mismatch":** כשה-tool וה-runtime בגרסאות שונות, ה-parser נכשל בזמן ריצה. שתיהן מוצמדות ל-`${antlr.version}`, וקוד ה-parser נוצר ב-build ולא נשמר ב-git.
2. **Spring Data Mongo לא יוצר אינדקסים מאז גרסה 3.0:** ברירת המחדל היא `auto-index-creation=false`, ובלי זה האינדקס הייחודי על name+version לא היה נוצר. הוגדר `true`.
3. **השוואת BigDecimal עם `equals`:** `22` ו-`22.0` אינם שווים לפי `equals`. ה-evaluator משתמש ב-`compareTo`, ויש טסט לזה.

## 2026-11-20 15:25–15:35 · mock-state-api
**מה נעשה:**
- אוכלוסייה סינתטית דטרמיניסטית בלי DB.
- מספרי זהות עם ספרת ביקורת.
- מרשם, ביטחוני, פלילי ו-summary.
- הזרקת תקלות (השהיה, אחוז כשלים, נפילה מלאה).

**בדיקות:** 6 טסטים עברו (domain).

**⚠️ באג ידוע שנמנע:** `Thread.sleep` להדמיית השהיה חוסם thread. עם virtual threads (`spring.threads.virtual.enabled`) זה כמעט חינמי. בלי זה, השהיה של 2 שניות ב-200 בקשות לשנייה הייתה מרוקנת את ה-thread pool של Tomcat (200 threads).

---

## פערים להוספה ל-`docs/GAPS.md` (במיזוג)
| # | תחום | הפער | למה | מה עשינו במקום | מה נדרש לפרודקשן | חומרה | מאמץ |
|---|---|---|---|---|---|---|---|
| G-19 | מדיניות | הסימולציה רצה על בקשות סינתטיות | אין עדיין היסטוריית בקשות אמיתית | 1,000–10,000 מקרים דטרמיניסטיים (seed קבוע) | הרצה חוזרת על בקשות אמיתיות מהחודש האחרון מ-permit-service (read replica) | 🟠 | S |
| G-20 | מדיניות | אירוע `policy.published` נכתב ללוג ולא דרך outbox | Mongo צריך replica set לטרנזקציה מרובת מסמכים | port מוכן (`PolicyEventPublisher`) ו-snapshot שמתרענן כל 30 שניות | Mongo replica set ו-outbox, או CDC | 🟠 | S |
| G-21 | מדיניות | מיקום שגיאות סמנטיות (שורה/עמודה) כללי | ה-AST לא שומר מיקומים | שגיאות תחביר מדויקות; שגיאות סמנטיות עם הודעה ברורה | שמירת token positions ב-AST | 🟢 | S |
