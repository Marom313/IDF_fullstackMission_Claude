# היתר נהג הובלה (משאית) מהאזור לתוך ישראל
RULE "driver-transport" VERSION 1 EFFECTIVE 2026-01-01
APPLIES TO permit.type == DRIVER_TRANSPORT
REQUIRE FIELDS idNumber, photo, magneticCard, vehiclePlate, cargoType, crossingPoint
WHEN person.age >= 25
 AND person.securityStatus == CLEAR
 AND person.magneticCardValid
 AND vehicle.inspectionValid
 AND trip.cargoType IN [FOOD, CONSTRUCTION_MATERIALS, AGRICULTURE_PRODUCE, GENERAL_GOODS]
 AND NOT sanctions.activeFor(person)
THEN APPROVE VALIDITY 30d
ELSE REJECT REASON "בקשת היתר נהג אינה עומדת בתנאים"
