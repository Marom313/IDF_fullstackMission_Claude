# היתר כניסה לטיפול רפואי: מסלול הומניטרי, תוקף קצר
# v2: שדות לפי PermitType.MEDICAL; בדיקה ביטחונית פתוחה (PENDING) אינה חוסמת טיפול רפואי
RULE "medical-entry" VERSION 2 EFFECTIVE 2026-01-01
APPLIES TO permit.type == MEDICAL
REQUIRE FIELDS idNumber, photo, hospitalName, appointmentDate
WHEN person.securityStatus IN [CLEAR, PENDING]
 AND daysUntil(form.appointmentDate) >= 0
 AND daysUntil(form.appointmentDate) <= 30
THEN APPROVE VALIDITY 3d
ELSE REFER_TO_CLERK REASON "בקשה רפואית דורשת בדיקה פרטנית"
