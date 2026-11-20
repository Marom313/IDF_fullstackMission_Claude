# policy-service: מנוע החוקות של IPP

> נבנה על ידי סוכן הענן ב-branch `cloud/policy-and-mock`. מיועד למיזוג על ידי הסוכן ב-VS Code.

## מה השירות עושה
- **כתיבת חוקות ב-DSL** (שפה ייעודית). הדקדוק נמצא ב-`src/main/antlr4/.../PolicyDsl.g4`.
- **ולידציה** עם שגיאות לפי שורה ועמודה, לסימון בעורך של Policy Studio.
- **גרסאות:** DRAFT → ACTIVE → RETIRED. בכל רגע יש גרסה פעילה אחת לכל חוקה.
- **SHA-256** על הצורה הקנונית של כל גרסה, כדי שה-audit יוכל להוכיח איזו חוקה בדיוק קיבלה כל החלטה.
- **הערכה** של מקרה מול כל החוקות הפעילות. אם כמה חוקות חלות, התוצאה המחמירה גוברת (deny-overrides).
- **הסבר להחלטה:** עץ נימוקים מלא (`trace`) ורשימת סיבות בעברית.
- **לוגיקה תלת-ערכית:** חסר נתון (למשל המרשם לא זמין) מוביל ל-UNKNOWN, ומשם להפניה לפקיד. אף פעם לא לאישור.
- **סימולציה:** איך טיוטה הייתה מחליטה על N בקשות, לעומת הגרסה הפעילה.
- **שדות חובה לטופס דינמי** לפי סוג ההיתר.

## שכבות (Hexagonal)
```
domain/        טהור: AST, parser (ANTLR), printer, codec, validator, evaluator, simulator. בלי Spring
application/   use cases (PolicyCatalogService) ו-ports. בלי Spring
adapters/web   REST, DTOs, Problem Details
adapters/persistence  Mongo (אינדקס ייחודי name+version, ו-@Version לנעילה אופטימית)
config/        חיווט Spring, אבטחה (Keycloak roles), טעינת חוקות דמו
```
ArchUnit (`ArchitectureTest`) אוכף את הגבולות.

## API (פורט 8080 בתוך compose, כמו כל השירותים)
| Method | Path | תיאור |
|---|---|---|
| POST | `/api/policies/validate` | בדיקת טקסט בלי שמירה |
| POST | `/api/policies` | שמירת טיוטה (201) |
| GET | `/api/policies` | הגרסה האחרונה של כל חוקה |
| GET | `/api/policies/{name}/versions` | כל הגרסאות |
| GET | `/api/policies/{name}/versions/{v}` | פרטי גרסה: מקור, צורה קנונית, AST |
| POST | `/api/policies/{name}/versions/{v}/publish` | פרסום (אידמפוטנטי) |
| GET | `/api/policies/{name}/diff?from=&to=` | השוואה בין גרסאות |
| POST | `/api/policies/simulate` | `{source, caseCount, seed}` |
| POST | `/api/policies/evaluate` | `{facts}`, ומחזיר החלטה, נימוקים ו-trace |
| POST | `/api/policies/required-fields` | `{facts: {permit: {type}}}` |
| GET | `/api/policies/vocabulary` | פונקציות ומקורות נתונים מותרים (השלמה אוטומטית) |
| POST | `/internal/policies/evaluate` | **החוזה של permit-service ב-main:** `{permitType, facts}` עם מפתחות שטוחים (`"person.age"`), ומחזיר `{decision, reasons, policyVersion, validityDays}` |
| GET | `/internal/policies/required-fields?permitType=` | **החוזה של permit-service:** מחזיר `{requiredFields}` |

`/internal/**` פתוח בלי JWT, כי ה-gateway לא מנתב אליו ולכן הוא נגיש רק מתוך רשת compose (פער: mTLS בפרודקשן).

Swagger: `/swagger-ui.html`

## חוזה ה-Facts (מה שולחים ל-evaluate)
```json
{
  "person":   {"age": 31, "securityStatus": "CLEAR", "magneticCardValid": true, "hasOpenCase": false},
  "employer": {"sector": "CONSTRUCTION"},
  "permit":   {"type": "WORK_ENTRY"},
  "quotas":   {"CONSTRUCTION": {"remaining": 120, "total": 5000}},
  "sanctions":{"active": false},
  "vehicle":  {"inspectionValid": true},
  "trip":     {"cargoType": "FOOD"},
  "form":     {"idNumber": "...", "photo": "...", "...": "..."},
  "today":    "2026-11-20"
}
```
סוגי היתר בחוקות הדמו, תואמים ל-`PermitType` ב-main: `WORK_ENTRY` (שלוש חוקות לפי ענף), `DRIVER`, `MEDICAL`. סטטוס ביטחוני: `CLEAR` / `PENDING` / `DENIED`.
אם לא נשלח `form`, שדות החובה לא נבדקים (permit-service בודק את הטופס בעצמו).

## הגדרות
| משתנה | ברירת מחדל | הערה |
|---|---|---|
| `MONGO_POLICY_PASSWORD` | `dev_mongo_policy` | המשתמש `policy_svc` מ-`infra/mongo/init-users.js` |
| `IPP_SECURITY_ENABLED` | `true` | בטסטים: `false` |
| `JWK_SET_URI` | Keycloak בתוך compose | כמו בשאר השירותים |
| `IPP_POLICY_SEED` | `true` | טוען את `resources/policies/*.ipp` ומפרסם אותם |

## ✅ מה נבדק
- **נבדק בענן:** domain ו-application, 35 טסטים (כולל החוזה השטוח של permit-service וטבלת שדות החובה לפי `PermitType`).
- **לא קומפל בענן** (הגישה ל-Maven Central חסומה שם): adapters, config, ו-integration tests.
- **מוכן למיזוג:** package `il.ipp.policy`, parent הוא `ipp-parent`, פורט 8080, ובנייה דרך `services/Dockerfile` המשותף.
- **TODO במיזוג (הסוכן ב-VS Code):**
  1. להוסיף `<module>policy-service</module>` ו-`<module>mock-state-api</module>` ל-`services/pom.xml`.
  2. להוסיף ל-compose לפי התבנית של `spring-service`: `MONGO_POLICY_PASSWORD`, ו-depends_on על mongo ו-keycloak. פורט דיבוג מוצע: `127.0.0.1:8087`.
  3. להריץ `mvn -f services/pom.xml verify`.
  4. אופציונלי: להחליף את `PolicyEventPublisher` ב-outbox המשותף.
