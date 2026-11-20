# ADR 0001 – שירותים לפי Bounded Contexts
**סטטוס:** מאושר · 20/11/2026

## הקשר
המערכת צריכה להדגים סקייל ועמידות, ועדיין להיבנות ולהיות מוסברת בזמן קצר.

## חלופות
1. **Monolith מודולרי**: הכי פשוט, אבל לא מדגים סקייל עצמאי של הנתיב החם.
2. **הרבה microservices קטנים** (15 ומעלה): מסובך מדי לזמן ולמחשב פיתוח.
3. **6 שירותי דומיין, gateway ו-mock-state-api** (נבחר).

## החלטה
registry, permit, policy, eligibility, enforcement ו-audit. כל אחד בעלים של הנתונים שלו (ראו "מי הבעלים של מה"). ה-gateway מטפל ב-JWT, ב-rate limit וב-SSE.

| ישות | בעלים | עותקים (read models) |
|---|---|---|
| Person, Employer, Photo | registry-service | eligibility, enforcement |
| Permit, License, Quota, Application | permit-service | eligibility, enforcement |
| Policy/Rule | policy-service | permit (תוצאת הערכה בלבד) |
| Eligibility read-model | eligibility-service | – |
| Violation, Case, Sanction, Appeal | enforcement-service | eligibility |
| Audit record | audit-service | – |

## השלכות
- **בעד:** סקייל של eligibility בנפרד (`--scale eligibility-service=3`), וגבולות ברורים בין שירותים.
- **נגד:** עקביות בסופו של דבר (eventual consistency) בין שירותים, מה שמחייב outbox ו-inbox (ADR 0003).
