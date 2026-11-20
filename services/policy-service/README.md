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

## API (פורט 8084)
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
סוגי היתר בחוקות הדמו: `WORK_ENTRY`, `DRIVER_TRANSPORT`, `MEDICAL`, `EXIT_ABROAD`.

## הגדרות
| משתנה | ברירת מחדל | הערה |
|---|---|---|
| `MONGO_URI` | `mongodb://localhost:27017/ipp_policies` | ב-compose: `mongodb://ipp_admin:${MONGO_ROOT_PASSWORD}@mongo:27017/ipp_policies?authSource=admin` |
| `IPP_SECURITY_ENABLED` | `false` | ב-compose: `true` |
| `KEYCLOAK_ISSUER_URI` | ריק | חובה כש-security פעיל |
| `IPP_POLICY_SEED` | `true` | טוען את `resources/policies/*.ipp` ומפרסם אותם |

## ✅ מה נבדק ומה לא (חשוב לסוכן ב-VS Code)
- **נבדק בענן:** domain ו-application. ה-parser נוצר מהדקדוק עם ANTLR 4.13.2 וקומפל עם javac; 31 טסטים עברו (`PolicyParserTest`, `EvaluatorTest`, `PolicyCatalogServiceTest`).
- **לא קומפל בענן** (הגישה ל-Maven Central חסומה שם): `adapters/`, `config/`, `PolicyApiIntegrationTest`, `ArchitectureTest`.
- **TODO במיזוג:**
  1. להחליף את ה-`<parent>` ב-pom ל-parent של ה-monorepo.
  2. להריץ `mvn verify` ולתקן מה שצריך.
  3. להוסיף את השירות ל-compose (Mongo, Keycloak, `mem_limit: 512m`) ולנתב ב-gateway את `/api/policies/**`.
  4. להחליף את `PolicyEventPublisher` (כרגע רק כותב ללוג) ב-outbox המשותף.
