package il.ipp.policy.domain.eval;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.List;

/**
 * Whitelist of functions the DSL may call. Anything not registered here is a validation error,
 * which is what keeps the DSL safe: policy authors cannot reach arbitrary Java code.
 * <p>
 * Key format: {@code "quota"} for a root call ({@code quota("CONSTRUCTION")}),
 * {@code "sanctions.activeFor"} for a call on a fact ({@code sanctions.activeFor(person)}).
 * Each function receives the evaluated arguments and the case facts; it returns a value
 * (Map / Number / Boolean / String) or {@code null} for "unknown".
 */
public final class FunctionRegistry {

    private final Map<String, BiFunction<List<Object>, Facts, Object>> functions = new TreeMap<>();

    public FunctionRegistry register(String key, BiFunction<List<Object>, Facts, Object> fn) {
        functions.put(key, fn);
        return this;
    }

    public Optional<BiFunction<List<Object>, Facts, Object>> find(String key) {
        return Optional.ofNullable(functions.get(key));
    }

    public Set<String> names() { return functions.keySet(); }

    /**
     * Standard IPP functions. They read pre-computed facts supplied by the caller (eligibility-service /
     * permit-service), so evaluation stays pure and fast: no I/O inside the policy engine.
     * <ul>
     *   <li>{@code quota(sector)} → {@code facts.quotas[sector]} (map with {@code remaining}, {@code total})</li>
     *   <li>{@code sanctions.activeFor(person)} → {@code facts.sanctions.active} (boolean)</li>
     *   <li>{@code daysUntil(date)} → days from {@code facts.today} to the given date</li>
     * </ul>
     */
    public static FunctionRegistry standard() {
        return new FunctionRegistry()
                .register("quota", (args, facts) -> {
                    if (args.size() != 1 || args.getFirst() == null) return null;
                    Object quotas = facts.get("quotas");
                    return quotas instanceof Map<?, ?> q ? q.get(args.getFirst().toString()) : null;
                })
                .register("sanctions.activeFor", (args, facts) -> {
                    Object sanctions = facts.get("sanctions");
                    if (sanctions instanceof Map<?, ?> s && s.get("active") instanceof Boolean b) return b;
                    return null;
                })
                .register("daysUntil", (args, facts) -> {
                    if (args.size() != 1 || args.getFirst() == null) return null;
                    Object today = facts.get("today");
                    if (today == null) return null;
                    java.time.LocalDate from = java.time.LocalDate.parse(today.toString());
                    java.time.LocalDate to = java.time.LocalDate.parse(args.getFirst().toString());
                    return java.math.BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(from, to));
                });
    }
}
