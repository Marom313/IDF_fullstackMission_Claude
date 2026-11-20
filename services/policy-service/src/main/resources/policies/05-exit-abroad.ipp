# בדיקת זכאות ליציאה לחו"ל דרך מעבר בינלאומי
RULE "exit-abroad" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == EXIT_ABROAD
REQUIRE FIELDS idNumber, photo, passportNumber, crossingPoint
WHEN person.securityStatus == CLEAR
 AND NOT sanctions.activeFor(person)
 AND (person.hasOpenCase == false OR person.caseSeverity == LOW)
THEN APPROVE VALIDITY 14d
ELSE REFER_TO_CLERK REASON "יציאה לחו\"ל מחייבת אישור פקיד"
