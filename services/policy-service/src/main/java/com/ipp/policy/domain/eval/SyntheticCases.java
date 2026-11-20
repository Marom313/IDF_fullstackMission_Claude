package com.ipp.policy.domain.eval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic synthetic cases for policy simulation ("how would this draft decide on 1,000 applications?").
 * Same seed means same cases on every run, so a simulation result is reproducible in a demo and in tests.
 * <p>
 * GAP: production would replay recent real applications from permit-service (see docs/GAPS.md).
 */
public final class SyntheticCases {

    private static final String[] SECTORS = {"CONSTRUCTION", "AGRICULTURE", "INDUSTRY", "SERVICES"};
    private static final String[] PERMIT_TYPES = {"WORK_ENTRY", "WORK_ENTRY", "WORK_ENTRY", "WORK_ENTRY", "DRIVER", "MEDICAL"};
    private static final String[] CARGO = {"FOOD", "CONSTRUCTION_MATERIALS", "AGRICULTURE_PRODUCE", "GENERAL_GOODS", "CHEMICALS"};
    private static final String[] REGIONS = {"JENIN", "NABLUS", "TULKARM", "QALQILYA", "RAMALLAH", "BETHLEHEM", "HEBRON", "JERICHO"};

    private SyntheticCases() { }

    public static List<Facts> generate(int count, long seed, LocalDate today) {
        SplittableRandom rnd = new SplittableRandom(seed);
        List<Facts> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(one(rnd, i, today));
        return out;
    }

    private static Facts one(SplittableRandom rnd, int index, LocalDate today) {
        String sector = SECTORS[rnd.nextInt(SECTORS.length)];
        String type = PERMIT_TYPES[rnd.nextInt(PERMIT_TYPES.length)];
        int roll = rnd.nextInt(100);
        String security = roll < 85 ? "CLEAR" : roll < 95 ? "PENDING" : "DENIED";

        Map<String, Object> person = new LinkedHashMap<>();
        person.put("age", 18 + rnd.nextInt(45));
        person.put("securityStatus", security);
        person.put("magneticCardValid", rnd.nextInt(100) < 92);
        person.put("region", REGIONS[rnd.nextInt(REGIONS.length)]);
        boolean openCase = rnd.nextInt(100) < 7;
        person.put("hasOpenCase", openCase);
        person.put("caseSeverity", openCase ? (rnd.nextBoolean() ? "LOW" : "HIGH") : "NONE");

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("idNumber", String.format("9%08d", index));
        form.put("photo", "s3://ipp-photos/sim/" + index + ".png");
        form.put("magneticCard", "MC-" + index);
        form.put("employerId", "EMP-" + rnd.nextInt(200));
        form.put("workRegion", person.get("region"));
        form.put("vehiclePlate", "PL-" + rnd.nextInt(99999));
        form.put("cargoType", CARGO[rnd.nextInt(CARGO.length)]);
        form.put("destinationRegion", REGIONS[rnd.nextInt(REGIONS.length)]);
        form.put("hospitalName", "HOSPITAL-" + (1 + rnd.nextInt(5)));
        form.put("appointmentDate", today.plusDays(rnd.nextInt(-5, 45)).toString());
        form.put("passportNumber", "P" + rnd.nextInt(10_000_000));
        if (rnd.nextInt(100) < 3) form.remove("photo");             // a few incomplete applications

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", "SIM-" + index);
        data.put("person", person);
        data.put("employer", Map.of("sector", sector));
        data.put("permit", Map.of("type", type));
        data.put("vehicle", Map.of("inspectionValid", rnd.nextInt(100) < 90));
        data.put("trip", Map.of("cargoType", form.get("cargoType")));
        data.put("quotas", Map.of(
                "CONSTRUCTION", Map.of("remaining", rnd.nextInt(100) < 80 ? 120 : 0, "total", 5000),
                "AGRICULTURE", Map.of("remaining", 40, "total", 2000),
                "INDUSTRY", Map.of("remaining", 15, "total", 1000),
                "SERVICES", Map.of("remaining", 0, "total", 500)));
        data.put("sanctions", Map.of("active", rnd.nextInt(100) < 4));
        data.put("form", form);
        data.put("today", today.toString());
        return new Facts(data);
    }
}
