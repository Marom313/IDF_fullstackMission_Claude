# היתר נהג הובלה (משאית) מהאזור לתוך ישראל
# v2: סוג ההיתר ושדות החובה תואמים ל-PermitType.DRIVER ב-permit-service
RULE "driver-transport" VERSION 2 EFFECTIVE 2026-01-01
APPLIES TO permit.type == DRIVER
REQUIRE FIELDS idNumber, photo, magneticCard, vehiclePlate, cargoType, destinationRegion
WHEN person.age >= 25
 AND person.securityStatus == CLEAR
 AND person.magneticCardValid
 AND NOT sanctions.activeFor(person)
THEN APPROVE VALIDITY 30d
ELSE REFER_TO_CLERK REASON "בקשת היתר נהג דורשת בדיקת פקיד"
