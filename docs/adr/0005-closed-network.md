# ADR 0005 – רשת סגורה (Air-Gapped) כאילוץ על
**סטטוס:** מאושר · 20/11/2026

## החלטה
- אין CDN ואין קריאה יוצאת בזמן ריצה. כל הפונטים, האייקונים והספריות נארזים ב-build.
- כל תמונת Docker מוצמדת לגרסה מדויקת, בלי `latest`.
- כל הכלים (Maven, gitleaks ובהמשך Trivy) רצים בקונטיינרים מוצמדים, כך ש-`verify` זהה online ו-offline.
- כתובות הרג'יסטרים (`DOCKER_REGISTRY`, `NPM_REGISTRY` ו-`MAVEN_MIRROR_URL`) מוגדרות ב-`.env`.
- Keycloak מוגש דרך NGINX תחת `/auth`. כך יש issuer אחד (`http://localhost/auth/realms/ipp`) לדפדפן ולשירותים, ואין צורך בחריגים ב-CSP.
- קליטה מבחוץ עוברת דרך NiFi (אימות manifest חתום ב-Ed25519, SHA-256, סכמה ו-quarantine). בפרודקשן מאחורי diode ו-CDR, ראו G-04.

## השלכות
build ראשון דורש אינטרנט, או חבילה מ-`scripts/offline/bundle.sh`. פירוט ב-`docs/CLOSED_NETWORK.md`.
