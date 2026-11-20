package il.ipp.policy.domain;

import il.ipp.policy.domain.ast.PolicyDefinition;
import il.ipp.policy.domain.dsl.ParseResult;
import il.ipp.policy.domain.dsl.PolicyParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared helpers for domain tests. */
public final class PolicyTestSupport {

    public static final List<String> SEED_FILES = List.of(
            "01-work-construction.ipp", "02-work-agriculture.ipp", "03-work-general.ipp",
            "04-driver.ipp", "05-medical.ipp");

    public static final String CONSTRUCTION = """
            RULE "work-permit-construction" VERSION 3 EFFECTIVE 2026-12-01
            APPLIES TO permit.type == WORK_ENTRY
            REQUIRE FIELDS idNumber, photo, employerId
            WHEN person.age >= 22
             AND person.securityStatus == CLEAR
             AND employer.sector == CONSTRUCTION
             AND quota("CONSTRUCTION").remaining > 0
             AND NOT sanctions.activeFor(person)
            THEN APPROVE VALIDITY 90d
            ELSE REFER_TO_CLERK REASON "תנאי זכאות לא מתקיימים"
            """;

    private PolicyTestSupport() { }

    public static PolicyDefinition parseOk(String source) {
        ParseResult r = new PolicyParser().parse(source);
        if (!r.isValid()) throw new AssertionError("Expected valid policy, got " + r.errors());
        return r.definition();
    }

    public static String resource(String name) {
        try (InputStream in = PolicyTestSupport.class.getResourceAsStream("/policies/" + name)) {
            if (in == null) throw new AssertionError("Missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A worker who satisfies the construction policy. Tests mutate a copy to break one condition at a time. */
    public static Map<String, Object> goodWorker() {
        Map<String, Object> person = new HashMap<>(Map.of("age", 31, "securityStatus", "CLEAR", "magneticCardValid", true));
        Map<String, Object> form = new HashMap<>(Map.of("idNumber", "900000001", "photo", "s3://p/1.png",
                "employerId", "EMP-1", "magneticCard", "MC-1", "workRegion", "HEBRON"));
        Map<String, Object> data = new HashMap<>();
        data.put("person", person);
        data.put("employer", new HashMap<>(Map.of("sector", "CONSTRUCTION")));
        data.put("permit", new HashMap<>(Map.of("type", "WORK_ENTRY")));
        data.put("quotas", new HashMap<>(Map.of("CONSTRUCTION", new HashMap<>(Map.of("remaining", 10, "total", 100)))));
        data.put("sanctions", new HashMap<>(Map.of("active", false)));
        data.put("form", form);
        data.put("today", "2026-11-20");
        return data;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> sub(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }
}
