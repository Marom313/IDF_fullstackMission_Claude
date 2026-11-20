# היתר כניסה לעבודה בענף הבנייה
RULE "work-permit-construction" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == WORK_ENTRY AND employer.sector == CONSTRUCTION
REQUIRE FIELDS idNumber, photo, magneticCard, employerId, workRegion
WHEN person.age >= 22
 AND person.securityStatus == CLEAR
 AND person.magneticCardValid
 AND quota("CONSTRUCTION").remaining > 0
 AND NOT sanctions.activeFor(person)
THEN APPROVE VALIDITY 90d
ELSE REFER_TO_CLERK REASON "תנאי זכאות לעבודה בבנייה לא מתקיימים"
