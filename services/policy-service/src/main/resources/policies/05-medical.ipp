# היתר כניסה לטיפול רפואי: מסלול הומניטרי. בדיקה ביטחונית פתוחה אינה חוסמת, רק סירוב
RULE "medical-permit" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == MEDICAL
REQUIRE FIELDS idNumber, photo, hospitalName, appointmentDate
WHEN person.securityStatus IN [CLEAR, PENDING]
THEN APPROVE VALIDITY 3d
ELSE REFER_TO_CLERK REASON "בקשה רפואית עם סירוב ביטחוני דורשת החלטת פקיד"
