# WORKLOG – יומן עבודה

## 20/11/2026 13:00 · שלב 0: שלד ותשתית
**מה נעשה:**
- שלד monorepo.
- compose עם 9 רכיבי תשתית: Postgres, PgBouncer, Mongo, Redis, Kafka KRaft, kafka-ui, Keycloak, SeaweedFS ו-NGINX.
- realm של Keycloak עם 9 משתמשי דמו.
- `ipp.ps1`/`Makefile` ו-`scripts/verify.sh`.
- gitleaks ו-CI.
- CLAUDE.md, SPEC ו-ADRs 0001–0006.

**למה:** כדי שיהיה בסיס יציב שעולה בפקודה אחת ולא מעמיס על המחשב. התשתית לבדה צורכת כ-**1.4GB** (נמדד).

**קבצים עיקריים:** `compose.yaml`, `infra/postgres/postgresql.conf`, `infra/pgbouncer/pgbouncer.ini`, `infra/nginx/nginx.conf`, `infra/keycloak/ipp-realm.json`, `scripts/verify.sh`.

**פקודות ותוצאות:**
- `docker compose up -d --wait`: כל 9 הרכיבים healthy.
- בקשת token ל-`officer1` דרך `http://localhost/auth`: ה-issuer הוא `http://localhost/auth/realms/ipp` וה-role הוא `CROSSING_OFFICER`.
- `bash scripts/verify.sh`: ירוק (compose config, כלל ההערות, gitleaks).

**מושגים:**
- **Healthcheck ו-`depends_on: service_healthy`:** Docker בודק כל כמה שניות אם הרכיב באמת מוכן, ולא רק אם התהליך רץ. שירות שתלוי ב-Postgres יעלה רק אחרי ש-`pg_isready` מצליח, וכך נמנעות שגיאות "connection refused" בזמן העלייה.
- **PgBouncer במצב transaction:** מתווך חיבורים. 500 לקוחות חולקים כ-80 חיבורים אמיתיים ל-Postgres, וכל חיבור מוחזק רק לאורך טרנזקציה אחת. זה חוסך זיכרון, כי כל חיבור ל-Postgres הוא תהליך של כמה MB.
- **KRaft:** Kafka מנהל את ה-metadata בעצמו (Raft), בלי ZooKeeper. יוצא תהליך אחד פחות ופחות זיכרון.
- **Keycloak מאחורי NGINX ב-`/auth`:** הדפדפן והשירותים רואים את אותו issuer, ולכן אימות ה-JWT לא נכשל על אי-התאמה ביניהם.

**⚠️ באגים ידועים שנמנעו:**
1. **NGINX לא עולה כשה-upstream עוד לא קיים** ("host not found in upstream"). הפתרון: `resolver 127.0.0.11` ו-`proxy_pass` עם משתנה, כך שה-DNS נפתר מחדש בכל בקשה.
2. **Keycloak 26 נכשל עם "Multiple garbage collectors selected".** Keycloak כבר בוחר GC בעצמו, לכן אסור להוסיף `-XX:+UseSerialGC` ל-`JAVA_OPTS_KC_HEAP`. נתקלנו בזה ותיקנו.
3. **`postgresql.conf` מותאם מחזיר את `listen_addresses` ל-localhost,** ואז אף קונטיינר לא מצליח להתחבר. הוגדר במפורש `'*'`.
4. **ב-Keycloak 24 ומעלה, משתמש בלי email, firstName או lastName מקבל "Update profile" בכניסה הראשונה.** לכן לכל משתמש דמו ב-realm יש את כל השדות.

**commit:** `feat(infra): phase 0 skeleton 1.1.3`

## 20/11/2026 15:40 · שלב 1: שירותי backend (registry, permit, eligibility, enforcement, audit, gateway)
**מה נעשה:** `java-common` (outbox, inbox, idempotency, Problem Details, אבטחה, מיסוך PII, correlation-id, DLT) ו-6 שירותים: gateway, registry, permit, eligibility, enforcement ו-audit. בנוסף שירות `migrate` (Liquibase) ו-seed דטרמיניסטי: 5,000 אנשים, 200 מעסיקים, 8,000 היתרים ו-50,000 מעברים.

**למה:** כל זרימת הליבה עובדת מקצה לקצה כבר עכשיו: seed, Kafka, read-models, בדיקת זכאות ירוק/אדום.

**פקודות ותוצאות:**
- `mvn -T 2 test` (בקונטיינר עם 2GB): כל המודולים ירוקים.
- `docker compose up --wait`: 15 קונטיינרים healthy, בסך הכל **~2.9GB RAM**.
- בדיקת זכאות לחמשת אנשי הדמו:

| ת"ז | תוצאה | סיבה |
|---|---|---|
| 900000001 | ALLOW | VALID_PERMIT |
| 900000003 | DENY | ACTIVE_SANCTION:ENTRY_BAN |
| 900000004 | REFER_TO_CLERK | SECURITY_PENDING |
| 900000005 | DENY | NO_VALID_PERMIT |

- קריאה שנייה מחזירה `cacheHit=true`.
- נבדקו גם: PDF SPECIMEN (31KB), QR חתום, תור פקיד (keyset), ו-RBAC (worker מקבל 403 על בדיקת זכאות).

**מושגים:**
- **Transactional Outbox:** האירוע נכתב לטבלה **באותה טרנזקציה** של השינוי העסקי, ו-relay שולח אותו ל-Kafka. אם השירות נופל באמצע, האירוע לא אובד ולא נשלח בלי שהנתון נשמר.
- **Inbox (idempotent consumer):** Kafka מבטיח "לפחות פעם אחת". `insert ... on conflict do nothing` על `(message_id, consumer)` מבטיח שהטיפול קורה רק פעם אחת.
- **CQRS-lite:** eligibility לא שואל אף שירות בזמן הבדיקה. יש לו read-model משלו (Postgres, ומעליו Redis) שנבנה מאירועים. כתיבה וקריאה מופרדות, וזה מה שמאפשר p95 נמוך.
- **Single-flight:** כשמפתח פג תוקף ו-100 בקשות מגיעות יחד, רק אחת בונה אותו מחדש (lock ב-Redis עם TTL של 2 שניות). כל השאר מחכות לה, וכך אין "עדר" שמפיל את ה-DB (cache stampede).
- **Virtual threads (Java 21):** threads קלים ש-JVM מנהל בעצמו. אלפי בקשות שממתינות ל-IO לא תופסות threads של מערכת ההפעלה.
- **Keyset pagination:** `WHERE (submitted_at, id) > (:last) ORDER BY ... LIMIT n` במקום `OFFSET`. הזמן קבוע גם בעמוד ה-1,000, ואין כפילויות כשנוספות שורות.

**⚠️ באגים ידועים שנמנעו:**
1. **resilience4j-spring-boot3 עם version מפורש התנגש ב-BOM של Spring Cloud** (`NoClassDefFoundError: RxJava3FallbackDecorator`). הפתרון: להשאיר את הגרסה ל-BOM. נתקלנו בזה ותיקנו.
2. **מיסוך PII מחק ננו-שניות בחותמות זמן** (9 ספרות אחרי נקודה). ה-regex עודכן, ונוספה בדיקה.
3. **PgBouncer במצב transaction מול JDBC:** `prepared statement "S_1" already exists`. הפתרון: `prepareThreshold=0` ב-URL של כל שירות.
4. **ArchUnit JUnit5 engine דיווח 0 בדיקות תחת surefire.** הבדיקות הוסבו ל-`@Test` רגילים עם `rule.check()`, כך שבטוח שהן רצות.
5. **מחיקת cache לפני commit** הייתה עלולה להחזיר ל-Redis מצב ישן. לכן ה-evict מתבצע ב-`afterCommit`.

## 20/11/2026 16:00 · אינטגרציה: policy-service ו-mock-state-api (מסוכן הענן)
**מה נעשה:**
- מיזוג `cloud/policy-and-mock`. היומן של סוכן הענן נמצא ב-`docs/worklog/cloud-agent.md`.
- שני המודולים עברו ל-parent של ה-monorepo, נוספו ל-compose (Mongo user ייעודי ו-JWT מ-Keycloak) ול-gateway (`/api/policies/**`, `/api/state/**`, `/api/chaos/**`).
- ה-adapter של permit-service עודכן לחוזה האמיתי: facts מקוננים, `POST /api/policies/evaluate`, ומיפוי `APPROVE/REJECT/REFER/NOT_APPLICABLE/INCOMPLETE`. ה-JWT של המשתמש מועבר הלאה (zero-trust).

**תוצאה מקצה לקצה:**
1. מעסיק מגיש בקשה, ו-policy-service מחזיר `work-permit-construction@v1` עם נימוקים בעברית.
2. פקיד מאשר.
3. Kafka.
4. בדיקה במעבר: `ALLOW`, `pv=work-permit-construction@v1`.

נבדקו גם:
- **Idempotency-Key:** אותו מפתח מחזיר את אותה בקשה.
- **chaos** (`outage=true`): ההמלצה הופכת ל-`REFER_TO_CLERK` עם `STATE_SYSTEMS_UNAVAILABLE`, ואישור נחסם עם 503. זה fail-safe.

**מושג:** **Zero-trust בין שירותים.** permit-service לא "סומך" על הרשת הפנימית. הוא מעביר את ה-JWT של המשתמש, ו-policy-service בודק אותו שוב. גם מי שחדר לרשת הפנימית לא יכול לפרסם חוקה.

**⚠️ באגים ידועים שנמנעו:**
1. **mock-state מחזיר 400 על ת"ז בלי ספרת ביקורת תקינה.** במימוש הראשון זה פתח את ה-circuit breaker. 4xx הוא תשובה עסקית ולא תקלה, ולכן הוא נתפס ב-adapter ולא נספר ככשל.
2. **`issuer-uri` בלבד ב-resource server גורם ל-discovery מול `localhost` מתוך הקונטיינר, וזה נכשל.** הוגדרו גם `jwk-set-uri` (פנימי) וגם `issuer-uri` (לאימות ה-claim בלבד).
3. **מיזוג pom עם regex:** `<parent>` שהופיע בתוך הערת XML נבלע. תוקן ידנית. לקח: לא לערוך XML עם regex חמדני.

## 20/11/2026 16:30 · שלב 1–4: פרונט (React + TypeScript, MVVM)
**מה נעשה:** SPA מלא עם 11 מסכים:
- מסך מעבר (טאבלט): ירוק/אדום, תמונה, נימוקים מתורגמים, זמן תגובה, אימות QR offline ב-Ed25519, ורישום כניסה ויציאה.
- פרופיל עובד: היתרים, QR ו-PDF SPECIMEN.
- טופס בקשה דינמי לפי מדיניות.
- תור פקיד (virtualized עם keyset).
- פורטל מעסיק: עובדים לפי אזור (ABAC), מכסות ובדיקת עובד.
- Policy Studio: ולידציה חיה, הדגשת תחביר, סימולציה על 1,000 מקרים, טיוטה ופרסום.
- אכיפה: תיקים ופעולות לפי מכונת מצבים.
- דשבורד.
- ניהול: DLT replay, הסגר ויומן ביקורת.
- דף בית והצהרת נגישות.

**ארכיטקטורה:**
- **MVVM:** model (TanStack Query ו-http), viewmodel (state, לוגיקה ופורמט), view (JSX בלבד). נאכף ב-eslint-plugin-boundaries: view שמייבא model הוא שגיאת lint.
- **טיפוסים:** נוצרים מ-OpenAPI (`npm run gen:api` עם openapi-typescript).
- **i18n:** 228 מפתחות × 4 שפות, בדיקה ב-CI, `<html lang dir>` אוטומטי ו-`<bdi>` לנתונים מעורבים.
- **SSE:** ה-gateway משדר "מה השתנה" בלבד, והפרונט מבטל queries (invalidate).
- **חבילה offline:** הפונטים (Heebo, Noto Arabic, Noto Cyrillic) נארזים מקומית.

**תוצאות:**
- `ipp verify` ירוק: tsc strict, eslint, stylelint (logical properties), i18n, 16 בדיקות Vitest כולל axe, ו-build.
- ה-SPA מוגש מ-NGINX עם CSP קשיח, והתחברות ב-PKCE מול Keycloak עובדת.

**מושגים:**
- **MVVM:** ה-ViewModel הוא hook שמחזיר את כל מה שהמסך צריך, מוכן לתצוגה. ה-View "טיפש" וקל לבדיקה, וה-ViewModel נבדק עם `renderHook` בלי DOM מסובך.
- **PKCE:** הדפדפן מייצר סוד חד-פעמי (code_verifier) ושולח רק hash שלו. גם מי שיירט את ה-code לא יכול להחליף אותו ל-token. כך אפליקציה ציבורית לא צריכה client secret.
- **Virtualization:** רק השורות שנראות על המסך קיימות ב-DOM. 10,000 שורות נגללות חלק.

**⚠️ באגים ידועים שנמנעו:**
1. **`<img src>` לא שולח Bearer token**, ולכן תמונות ו-PDF נטענים ב-fetch כ-blob ומוצגים עם `URL.createObjectURL`. ה-URL משוחרר ב-unmount כדי למנוע דליפת זיכרון.
2. **EventSource לא תומך ב-headers.** SSE ממומש ב-fetch streaming עם reconnect.
3. **vitest 2 מביא vite 5 משלו** (התנגשות טיפוסים עם vite 6). שודרג ל-vitest 3.
4. **TanStack Virtual משתמש ב-style attributes**, ו-CSP עם `style-src 'self'` חוסם אותם. נוסף רק `style-src-attr 'unsafe-inline'`, בלי `<style>` inline ובלי סקריפטים inline.

## 20/11/2026 17:00 · שלבים 3–4: קליטה, k6, דמו
**מה נעשה:**
- `ingest-service`, צנרת הלבנה ב-9 שלבים (ADR 0007, הוחלט במקום NiFi).
- `scripts/ingest/make-batch.mjs` ליצירת חבילות חתומות, כולל `--tamper` ו-`--badsig`.
- k6.
- `DEMO_SCRIPT.md`.
- סקריפטי offline (`bundle.sh`, `load.sh`).
- פקודות `perf`, `batch` ו-`chaos`.

**תוצאות:**
- **k6, 200 RPS למשך 60 שניות:** p95=**20.3ms**, p99=92ms, 0% שגיאות, 12,000 בקשות, cache hit **95.8%**. נשמר ב-`docs/perf/`.
- **סגירת מעגל:** batch חתום עם כניסה של 900000005 (בלי היתר) עובר ingest, Kafka, ואז enforcement (STAY_WITHOUT_PERMIT), מניעת כניסה אוטומטית, ו-eligibility DENY. הכול תוך שניות.
- **חתימה פסולה:** הקובץ עובר ל-`ingest/quarantine` עם `.reason`, ומוצג במסך הניהול.
- **`--scale eligibility-service=3`:** כל המופעים healthy, אבל ה-gateway לא מפזר עומס (G-32).

**מושג:** **Ed25519 על manifest במקום על הקובץ.** חותמים על מסמך קטן שמכיל את ה-SHA-256 של הקובץ. מאמתים את החתימה, ואז משווים hash. כך לא צריך לחתום על קבצים גדולים, ואותו manifest משמש ל-dedup ול-audit.

**⚠️ באגים ידועים שנמנעו:**
1. **"קובץ חצי כתוב":** הצנרת מתחילה רק כשכל שלושת הקבצים קיימים, והחתימה נכתבת אחרונה.
2. **שליחה כפולה:** ל-`crossingId` יש מזהה דטרמיניסטי (hash של הקובץ ומספר השורה), וה-consumer הוא idempotent (inbox).
3. **מדידת k6 על JVM קר** (C1 בלבד, `TieredStopAtLevel=1`) הראתה p95 של כשנייה. אחרי חימום: 20ms. לכן בתסריט הדמו מחממים לפני.

## 20/11/2026 18:00 · מיזוג `cloud/align-with-main`
**מה נעשה:**
- חוקות v2 ל-DRIVER ול-MEDICAL, וחוקה חדשה לתעשייה ושירותים (`work-permit-general`).
- mock-state מזהה עכשיו את מספרי הזהות של SeedCatalog.
- ה-branch הישן `cloud/policy-and-mock` לא מוזג שוב.

**דמו מהיר:**
- driver1 שלח בקשת DRIVER וקיבל `APPROVE` (`driver-transport@v2`).
- worker1 שלח בקשת MEDICAL (תור בעוד 7 ימים) וקיבל `APPROVE` (`medical-entry@v2`).
- `ipp verify`: ירוק.
