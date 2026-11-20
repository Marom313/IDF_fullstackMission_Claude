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
