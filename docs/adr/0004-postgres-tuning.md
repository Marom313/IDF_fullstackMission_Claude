# ADR 0004 – כוונון PostgreSQL, PgBouncer ו-HikariCP
**סטטוס:** מאושר · 20/11/2026

כל שינוי מסומן בקובץ עצמו ב-`[MANUAL CHANGE – Marom]`, ו-`ipp verify` אוכף את הכלל (`scripts/check-config-comments.sh`).

## postgresql.conf
| פרמטר | ברירת מחדל | ערך | למה |
|---|---|---|---|
| listen_addresses | localhost | * | קובץ config מותאם; גישה מרשת compose |
| max_connections | 100 | 200 | PgBouncer מרכז את החיבורים; נשאר מקום ל-migrate ולכלים |
| superuser_reserved_connections | 3 | 3 | מתועד בלבד |
| statement_timeout | 0 | 30s (5s ל-roles של שירותים) | שאילתה תקועה לא מחזיקה חיבור |
| lock_timeout | 0 | 5s | כשל מהיר במקום שרשרת המתנות |
| idle_in_transaction_session_timeout | 0 | 60s | טרנזקציה נשכחת חוסמת VACUUM |
| idle_session_timeout | 0 | 0 גלובלי, 10min ל-role אנושי | הגדרה גלובלית מנתקת את PgBouncer |
| shared_buffers | 128MB | 256MB | כ-50% מ-512MB |
| work_mem | 4MB | 8MB | מיון בדשבורד בלי spill לדיסק |
| effective_cache_size | 4GB | 1GB | רמז ל-planner לפי גודל הקונטיינר |
| log_min_duration_statement | -1 | 500ms | לוג לשאילתות איטיות |
| shared_preload_libraries | '' | pg_stat_statements | סטטיסטיקה לכל שאילתה |

## pgbouncer.ini
| פרמטר | ברירת מחדל | ערך | למה |
|---|---|---|---|
| pool_mode | session | transaction | הרבה לקוחות על מעט חיבורי שרת |
| auth_type | md5 | scram-sha-256 | תואם ל-PG17 |
| max_client_conn | 100 | 500 | scale של כמה מופעים |
| default_pool_size | 20 | 20 | 4 users × 20 = 80 < 200 |
| server_reset_query | DISCARD ALL | ריק | לפי התיעוד, ל-transaction mode |
| ignore_startup_parameters | '' | extra_float_digits,search_path | הדרייבר של JDBC שולח אותם |

## HikariCP (לכל שירות)
**חישוב:** `maximumPoolSize = 10` לכל מופע. 3 מופעי eligibility × 10 = 30 חיבורי לקוח ב-PgBouncer, שמתמפים ל-20 חיבורי שרת לכל היותר.

הנוסחה הקלאסית `connections = (cores × 2) + spindles` נותנת בערך 2×2+1 = 5 לשירות עם 1 CPU. לקחנו 10 בגלל virtual threads, שממתינים הרבה על IO.

`connectionTimeout = 3s` (ברירת מחדל 30s): כשל מהיר. `leakDetectionThreshold = 20s` (ברירת מחדל 0): לוג על חיבור שלא הוחזר.

**חשוב:** במצב transaction ב-PgBouncer חובה `prepareThreshold=0` ב-JDBC URL. אחרת מתקבלת השגיאה `prepared statement "S_1" already exists`.
