# mock-state-api: דמה של ממשקי המדינה

> נבנה על ידי סוכן הענן ב-branch `cloud/policy-and-mock`. כל הנתונים סינתטיים (פער G-03 ב-`docs/GAPS.md`).

## מה זה
שירות שמחקה שלושה ממשקים חיצוניים שמערכת אמיתית הייתה פונה אליהם:
- **מרשם אוכלוסין:** שם בעברית, ערבית ואנגלית, תאריך לידה, אזור וכרטיס מגנטי.
- **סטטוס ביטחוני:** `CLEAR` / `REVIEW` / `DENIED`, עם קוד סיבה אטום בלבד.
- **רישום פלילי:** רישום קודם ותיק פתוח עם חומרה.

## איך הנתונים נוצרים
- **אין DB.** אדם מספר i נוצר תמיד מאותו seed ומ-i, כך שכל השירותים רואים את אותן עובדות על אותו מספר זהות.
- **מספרי זהות:** 9 ספרות עם ספרת ביקורת תקנית, בטווח `4xxxxxxxx` (קל לזהות שזה mock בלוגים).
- **התפלגות:** כ-85% CLEAR, 10% REVIEW, 5% DENIED; 92% עם כרטיס מגנטי בתוקף (הכרטיס מונפק ל-4 שנים); 7% עם תיק פתוח.
- גודל האוכלוסייה וה-seed נקבעים ב-`IPP_MOCK_POPULATION` (ברירת מחדל 5000) וב-`IPP_MOCK_SEED` (ברירת מחדל 42).

## API (פורט 8090)
| Method | Path | תיאור |
|---|---|---|
| GET | `/api/state/persons/{id}/summary` | שלושת המקורות בקריאה אחת (ל-eligibility-service) |
| GET | `/api/state/registry/persons/{id}` | מרשם |
| GET | `/api/state/security/{id}` | ביטחוני |
| GET | `/api/state/criminal/{id}` | פלילי |
| GET | `/api/state/registry/ids?offset=&limit=` | רשימת מספרי זהות ל-seed של שירותים אחרים |
| GET | `/api/state/validate-id/{id}` | בדיקת ספרת ביקורת |
| GET/PUT/DELETE | `/admin/chaos` | הזרקת תקלות להדגמה |

קודי שגיאה (Problem Details): `400 INVALID_ID`, `404 PERSON_NOT_FOUND`, `503 STATE_UNAVAILABLE`.

## הדגמת עמידות בראיון
```bash
# המרשם "איטי": 2 שניות + עד חצי שנייה רנדומלי, 30% כשלים
curl -X PUT localhost:8090/admin/chaos -H 'Content-Type: application/json' \
     -d '{"latencyMs":2000,"jitterMs":500,"failureRate":0.3,"outage":false}'
# נפילה מלאה
curl -X PUT localhost:8090/admin/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"jitterMs":0,"failureRate":0,"outage":true}'
# חזרה לתקין
curl -X DELETE localhost:8090/admin/chaos
```
המטרה: להראות שב-eligibility-service ה-circuit breaker נפתח, והבקשות מופנות לפקיד (`REFER_TO_CLERK`, כי ב-policy-service חסר נתון הוא UNKNOWN) במקום להיתקע או לאשר.

> ⚠️ **אבטחה:** את `/admin/**` אסור לנתב דרך ה-gateway ולחשוף לדפדפן. הנתיב פתוח רק ברשת הפנימית של compose וב-localhost.

## ✅ מה נבדק
- **נבדק בענן:** domain (IdNumbers, MockPopulation, Chaos). 6 טסטים עברו.
- **לא קומפל בענן:** Spring controllers ו-`StateApiIntegrationTest`. ירוצו במיזוג.
- **TODO במיזוג:**
  1. להחליף את ה-parent ב-pom.
  2. להוסיף את השירות ל-compose (`mem_limit: 384m`, בלי פורט חיצוני, או `127.0.0.1:8090` לדמו).
  3. לחבר את Resilience4j ב-eligibility-service.
