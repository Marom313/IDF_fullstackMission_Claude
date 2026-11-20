# היתר כניסה לטיפול רפואי: מסלול הומניטרי, תוקף קצר
RULE "medical-entry" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == MEDICAL
REQUIRE FIELDS idNumber, photo, hospitalReferral, appointmentDate
WHEN person.securityStatus IN [CLEAR, REVIEW]
 AND daysUntil(form.appointmentDate) >= 0
 AND daysUntil(form.appointmentDate) <= 30
THEN APPROVE VALIDITY 3d
ELSE REFER_TO_CLERK REASON "בקשה רפואית דורשת בדיקה פרטנית"
