package il.ipp.policy.application;

import il.ipp.policy.domain.eval.Facts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * permit-service sends facts as a flat map with dotted keys
 * ({@code "person.age": 31, "employer.sector": "CONSTRUCTION", "quota.CONSTRUCTION.remaining": 12}).
 * The engine works on nested maps. This converts one shape to the other, so both teams keep their natural format.
 * <p>
 * Also maps {@code quota.*} → {@code quotas.*} (the root the DSL's {@code quota(sector)} function reads)
 * and sets {@code permit.type} from the request.
 */
public final class FlatFacts {

    private FlatFacts() { }

    public static Facts toFacts(String permitType, Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (flat != null) {
            for (Map.Entry<String, Object> e : flat.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                if (e.getValue() instanceof Map<?, ?> nested && !e.getKey().contains(".")) {
                    root.put(e.getKey(), deepCopy(nested));    // already nested: keep as is
                    continue;
                }
                put(root, e.getKey().startsWith("quota.") ? "quotas." + e.getKey().substring(6) : e.getKey(), e.getValue());
            }
        }
        if (permitType != null) put(root, "permit.type", permitType);
        return new Facts(root);
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String dotted, Object value) {
        String[] parts = dotted.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(parts[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        cur.put(parts[parts.length - 1], value);
    }

    private static Map<String, Object> deepCopy(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v instanceof Map<?, ?> mm ? deepCopy(mm) : v));
        return out;
    }
}
