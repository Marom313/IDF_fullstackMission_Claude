# היתר נהג הובלה (משאית) מהאזור לתוך ישראל
RULE "driver-permit" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == DRIVER
REQUIRE FIELDS idNumber, photo, magneticCard, vehiclePlate, cargoType, destinationRegion
WHEN person.age >= 25
 AND person.securityStatus == CLEAR
THEN APPROVE VALIDITY 30d
ELSE REFER_TO_CLERK REASON "בקשת היתר נהג דורשת בדיקת פקיד"
