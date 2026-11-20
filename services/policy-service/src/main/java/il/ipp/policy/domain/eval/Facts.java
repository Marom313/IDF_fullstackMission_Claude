package il.ipp.policy.domain.eval;

import java.util.Collections;
import java.util.Map;

/**
 * The data a case is evaluated against, as a nested map, e.g.
 * <pre>{
 *   "person":   {"age": 31, "securityStatus": "CLEAR", "region": "HEBRON"},
 *   "employer": {"sector": "CONSTRUCTION"},
 *   "permit":   {"type": "WORK_ENTRY"},
 *   "quotas":   {"CONSTRUCTION": {"remaining": 120, "total": 5000}},
 *   "sanctions":{"active": false},
 *   "form":     {"idNumber": "...", "photo": "s3://...", ...},
 *   "today":    "2026-11-20"
 * }</pre>
 * {@code form} holds the submitted application fields that REQUIRE FIELDS checks.
 */
public record Facts(Map<String, Object> data) {

    public Facts {
        data = data == null ? Map.of() : Collections.unmodifiableMap(data);
    }

    public Object get(String key) { return data.get(key); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> form() {
        Object f = data.get("form");
        return f instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
