# מדריך העברה לרשת סגורה
> טיוטה. יעודכן בשלבים 3–4, כשיתווספו NiFi ו-bundle.

1. **בצד המחובר:** מריצים `bash scripts/offline/bundle.sh`, שמייצר את התיקייה `.offline-bundle/` עם:
   - `images.tar`: `docker save` לכל התמונות שב-compose.
   - `m2/`: מאגר Maven מלא, אחרי `dependency:go-offline`.
   - `npm-cache/`: נוצר עם `npm ci --cache`.
2. **העברה:** דרך שער ההלבנה הארגוני (diode ו-CDR), יחד עם קובץ `SHA256SUMS` חתום.
3. **בצד הסגור:** מריצים `bash scripts/offline/load.sh`, שמבצע `docker load` ומעתיק את ה-m2 ואת ה-cache.
4. **`.env`:** מפנים את `DOCKER_REGISTRY`, `NPM_REGISTRY` ו-`MAVEN_MIRROR_URL` ל-Nexus או ל-Artifactory הפנימי.
5. **CA פנימי:** מרכיבים truststore כ-volume, ומגדירים `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/certs/truststore.jks`.
6. **קליטת מידע:** קבצים נכנסים ל-`ingest/inbox` עם `manifest.json` ו-`manifest.sig`. בפרודקשן שלבים 2–4 מתבצעים בשער ההלבנה, ו-NiFi רק מאמת שוב.
