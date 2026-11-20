# mock-state-api: דמה של ממשקי המדינה

> נבנה על ידי סוכן הענן ב-branch `cloud/policy-and-mock`. כל הנתונים סינתטיים (פער G-03 ב-`docs/GAPS.md`).

## מה זה
שירות שמחקה שלושה ממשקים חיצוניים שמערכת אמיתית הייתה פונה אליהם:
- **מרשם אוכלוסין:** שם בעברית, ערבית ואנגלית, תאריך לידה, אזור וכרטיס מגנטי.
- **סטטוס ביטחוני:** `CLEAR` / `PENDING` / `DENIED`, עם קוד סיבה אטום בלבד.
- **רישום פלילי:** רישום קודם ותיק פתוח עם חומרה.

## איך הנתונים נוצרים
- **אין DB.** אדם מספר i נוצר תמיד מאותו seed ומ-i, כך שכל השירותים רואים את אותן עובדות על אותו מספר זהות.
- **אותו "עולם" כמו main (`SeedCatalog`):**
  - מספרי זהות `900000001`–`900000005` (אנשי הדמו) ו-`98xxxxxxx`.
  - סטטוס ביטחוני `CLEAR` / `PENDING` / `DENIED` לפי אותו כלל בדיוק.
  - נבדק מול הקוד של main על כל 5,000 האנשים, והכל תואם.
- **שאר השדות:** 92% עם כרטיס מגנטי בתוקף (מונפק ל-4 שנים); 7% עם תיק פתוח.

## API (פורט 8080 בתוך compose)
| Method | Path | תיאור |
|---|---|---|
| GET | `/api/state/persons/{id}/summary` | שלושת המקורות בקריאה אחת (ל-eligibility-service) |
| GET | `/api/state/registry/persons/{id}` | מרשם |
| GET | `/api/state/security/{id}` | ביטחוני |
| GET | `/api/state/criminal/{id}` | פלילי |
| GET | `/api/state/registry/ids?offset=&limit=` | רשימת מספרי זהות ל-seed של שירותים אחרים |
| GET | `/api/state/validate-id/{id}` | בדיקת ספרת ביקורת |
| GET/PUT/DELETE | `/admin/chaos` | הזרקת תקלות להדגמה |

קודי שגיאה (Problem Details): `400 INVALID_ID` (לא 9 ספרות), `404 PERSON_NOT_FOUND`, `503 STATE_UNAVAILABLE`.

## הדגמת עמידות בראיון
```bash
# המרשם "איטי": 2 שניות + עד חצי שנייה רנדומלי, 30% כשלים
curl -X PUT localhost:8088/admin/chaos -H 'Content-Type: application/json' \
     -d '{"latencyMs":2000,"jitterMs":500,"failureRate":0.3,"outage":false}'
# נפילה מלאה
curl -X PUT localhost:8088/admin/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"jitterMs":0,"failureRate":0,"outage":true}'
# חזרה לתקין
curl -X DELETE localhost:8088/admin/chaos
```
המטרה: להראות שב-eligibility-service ה-circuit breaker נפתח, והבקשות מופנות לפקיד (`REFER_TO_CLERK`, כי ב-policy-service חסר נתון הוא UNKNOWN) במקום להיתקע או לאשר.

> ⚠️ **אבטחה:** את `/admin/**` אסור לנתב דרך ה-gateway ולחשוף לדפדפן. הנתיב פתוח רק ברשת הפנימית של compose וב-localhost.

## ✅ מה נבדק
- **נבדק בענן:** domain (IdNumbers, MockPopulation, Chaos). 6 טסטים עברו.
- **לא קומפל בענן:** Spring controllers ו-`StateApiIntegrationTest`. ירוצו במיזוג.
- **TODO במיזוג:**
  1. module ב-`services/pom.xml` (ה-parent כבר הוחלף ל-`ipp-parent`).
  2. להוסיף את השירות ל-compose (`mem_limit: 384m`, ופורט דיבוג `127.0.0.1:8088`).
  3. לחבר את Resilience4j ב-eligibility-service.
