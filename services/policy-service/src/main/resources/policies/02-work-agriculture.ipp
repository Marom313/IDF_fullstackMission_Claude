# היתר כניסה לעבודה בחקלאות (עונתי, תוקף קצר יותר)
RULE "work-permit-agriculture" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == WORK_ENTRY AND employer.sector == AGRICULTURE
REQUIRE FIELDS idNumber, photo, magneticCard, employerId, workRegion
WHEN person.age >= 18
 AND person.securityStatus == CLEAR
 AND quota("AGRICULTURE").remaining > 0
THEN APPROVE VALIDITY 60d
ELSE REFER_TO_CLERK REASON "תנאי זכאות לעבודה בחקלאות לא מתקיימים"
