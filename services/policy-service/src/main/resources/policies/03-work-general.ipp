# היתר כניסה לעבודה בתעשייה ובשירותים: חוקה אחת לשני ענפים, המכסה נבדקת לפי ענף המעסיק
RULE "work-permit-general" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == WORK_ENTRY AND employer.sector IN [INDUSTRY, SERVICES]
REQUIRE FIELDS idNumber, photo, magneticCard, employerId, workRegion
WHEN person.age >= 21
 AND person.securityStatus == CLEAR
 AND quota(employer.sector).remaining > 0
THEN APPROVE VALIDITY 90d
ELSE REFER_TO_CLERK REASON "תנאי זכאות לעבודה לא מתקיימים"
