# ADR 0003 – Kafka עם Transactional Outbox ו-Idempotent Inbox
**סטטוס:** מאושר · 20/11/2026

## הקשר
שירות שכותב ל-DB וגם שולח לקפקא עלול להיכשל בין שתי הפעולות. זו בעיית ה-"dual write": נתון נשמר והאירוע אבד, או להפך.

## החלטה
- **Outbox:** השירות כותב את האירוע לטבלת `outbox` **באותה טרנזקציה** של הנתון העסקי. relay (polling כל 500ms עם `FOR UPDATE SKIP LOCKED`) מפרסם לקפקא ומסמן את האירוע כ-sent.
- **Inbox:** הצרכן מכניס `(message_id, consumer)` עם `ON CONFLICT DO NOTHING` באותה טרנזקציה של הטיפול. אם ההכנסה לא הוסיפה שורה, ההודעה כבר טופלה, ומדלגים עליה. כך מקבלים at-least-once שמתנהג כמו exactly-once.
- **Topics:** `<domain>.<entity>.<event>.v1`, עם key = personId כדי לשמור על סדר לכל אדם, ו-6 partitions.
- **Poison pills:** `ErrorHandlingDeserializer`, ו-`DefaultErrorHandler` עם 3 ניסיונות ו-backoff אקספוננציאלי. אחריהם `<topic>.DLT`, עם מסך replay.
- **KRaft:** Kafka בלי ZooKeeper (תהליך אחד פחות).

## השלכות
latency של עד כ-0.5 שניות בין כתיבה לפרסום, וזה מקובל. Debezium (CDC) נדחה, ראו G-10.
