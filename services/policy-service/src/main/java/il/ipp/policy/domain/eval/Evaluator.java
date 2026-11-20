package il.ipp.policy.domain.eval;

import il.ipp.policy.domain.ast.Action;
import il.ipp.policy.domain.ast.Node;
import il.ipp.policy.domain.ast.PolicyDefinition;
import il.ipp.policy.domain.dsl.AstPrinter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Interprets a {@link PolicyDefinition} against {@link Facts}. Pure, thread-safe and allocation-light:
 * no I/O, no reflection, no script engine. One evaluation is a single tree walk (microseconds), which is
 * what lets the eligibility hot path stay far below the 150ms p95 target.
 */
public final class Evaluator {

    private static final int MAX_REASONS = 5;

    private final FunctionRegistry functions;
    private final AstPrinter printer = new AstPrinter();

    public Evaluator(FunctionRegistry functions) {
        this.functions = Objects.requireNonNull(functions);
    }

    public Decision evaluate(PolicyDefinition p, Facts facts) {
        // 1. Scope
        if (p.appliesTo() != null) {
            Trace scope = eval(p.appliesTo(), facts);
            if (scope.result() != Tri.TRUE) {
                return new Decision(Decision.Outcome.NOT_APPLICABLE, p.name(), p.version(), null, List.of(),
                        List.of("המדיניות אינה חלה על הבקשה: " + scope.expression()), scope);
            }
        }
        // 2. Mandatory form fields. Only checked when the caller sends the form: permit-service validates
        //    the form itself (via required-fields) and asks for an eligibility-only evaluation.
        List<String> missing = new ArrayList<>();
        Map<String, Object> form = facts.form();
        List<String> required = facts.get("form") == null ? List.of() : p.requiredFields();
        for (String field : required) {
            Object v = form.get(field);
            if (v == null || (v instanceof String s && s.isBlank())) missing.add(field);
        }
        if (!missing.isEmpty()) {
            return new Decision(Decision.Outcome.INCOMPLETE, p.name(), p.version(), null, missing,
                    List.of("חסרים שדות חובה: " + String.join(", ", missing)), null);
        }
        // 3. Condition
        Trace trace = eval(p.when(), facts);
        return switch (trace.result()) {
            case TRUE -> toDecision(p, p.thenAction(), trace, List.of());
            case FALSE -> toDecision(p, p.elseAction(), trace, reasons(trace.leavesWith(Tri.FALSE)));
            case UNKNOWN -> new Decision(Decision.Outcome.REFER_TO_CLERK, p.name(), p.version(), null, List.of(),
                    prepend("חסר מידע להכרעה אוטומטית", reasons(trace.leavesWith(Tri.UNKNOWN))), trace);
        };
    }

    /** Does the policy's APPLIES TO match these (possibly partial) facts? TRUE when there is no scope. */
    public Tri scope(PolicyDefinition p, Facts facts) {
        return p.appliesTo() == null ? Tri.TRUE : eval(p.appliesTo(), facts).result();
    }

    private Decision toDecision(PolicyDefinition p, Action action, Trace trace, List<String> extraReasons) {
        Decision.Outcome outcome = switch (action.outcome()) {
            case APPROVE -> Decision.Outcome.APPROVE;
            case REJECT -> Decision.Outcome.REJECT;
            case REFER_TO_CLERK -> Decision.Outcome.REFER_TO_CLERK;
        };
        List<String> reasons = action.reason() == null ? extraReasons : prepend(action.reason(), extraReasons);
        return new Decision(outcome, p.name(), p.version(), action.validity(), List.of(), reasons, trace);
    }

    private static List<String> reasons(List<Trace> leaves) {
        return leaves.stream().limit(MAX_REASONS)
                .map(t -> t.detail() == null ? t.expression() : t.detail()).toList();
    }

    private static List<String> prepend(String first, List<String> rest) {
        List<String> out = new ArrayList<>(rest.size() + 1);
        out.add(first);
        out.addAll(rest);
        return out;
    }

    // ------------------------------------------------------------------ boolean evaluation

    Trace eval(Node node, Facts facts) {
        String text = printer.expr(node);
        return switch (node) {
            case Node.And a -> {
                Trace l = eval(a.left(), facts);
                Trace r = eval(a.right(), facts);
                yield new Trace(text, l.result().and(r.result()), null, List.of(l, r));
            }
            case Node.Or o -> {
                Trace l = eval(o.left(), facts);
                Trace r = eval(o.right(), facts);
                yield new Trace(text, l.result().or(r.result()), null, List.of(l, r));
            }
            case Node.Not n -> {
                Trace inner = eval(n.inner(), facts);
                // A NOT node is reported as a leaf so "reasons" show "NOT sanctions..." rather than the inner text.
                yield Trace.leaf(text, inner.result().not(), inner.detail() == null ? null : "NOT (" + inner.detail() + ")");
            }
            case Node.Compare c -> compare(c, text, facts);
            case Node.In in -> {
                Object left = value(in.left(), facts);
                if (left == null) yield Trace.leaf(text, Tri.UNKNOWN, describe(in.left()) + " חסר");
                boolean match = in.values().stream().anyMatch(v -> equalsLoose(left, literalValue(v)));
                yield Trace.leaf(text, Tri.of(match), describe(in.left()) + " = " + show(left));
            }
            case Node.Truthy t -> {
                Object v = value(t.operand(), facts);
                if (v instanceof Boolean b) yield Trace.leaf(text, Tri.of(b), describe(t.operand()) + " = " + b);
                yield Trace.leaf(text, Tri.UNKNOWN, describe(t.operand()) + (v == null ? " חסר" : " אינו ערך בוליאני"));
            }
            case Node.Ref r -> eval(new Node.Truthy(r), facts);
            case Node.Literal l -> l.value() instanceof Boolean b
                    ? Trace.leaf(text, Tri.of(b), null)
                    : Trace.leaf(text, Tri.UNKNOWN, "ליטרל שאינו בוליאני בתנאי");
        };
    }

    private Trace compare(Node.Compare c, String text, Facts facts) {
        Object left = value(c.left(), facts);
        Object right = value(c.right(), facts);
        String detail = describe(c.left()) + " = " + show(left) + " (נדרש " + c.op().symbol() + " " + show(right) + ")";
        if (left == null || right == null) {
            return Trace.leaf(text, Tri.UNKNOWN, describe(left == null ? c.left() : c.right()) + " חסר");
        }
        Integer cmp = compareValues(left, right);
        Tri result = switch (c.op()) {
            case EQ -> Tri.of(equalsLoose(left, right));
            case NEQ -> Tri.of(!equalsLoose(left, right));
            case GT -> cmp == null ? Tri.UNKNOWN : Tri.of(cmp > 0);
            case GTE -> cmp == null ? Tri.UNKNOWN : Tri.of(cmp >= 0);
            case LT -> cmp == null ? Tri.UNKNOWN : Tri.of(cmp < 0);
            case LTE -> cmp == null ? Tri.UNKNOWN : Tri.of(cmp <= 0);
        };
        return Trace.leaf(text, result, detail);
    }

    // ------------------------------------------------------------------ value resolution

    /** Resolves an operand to a normalised value (BigDecimal / String / Boolean / LocalDate / Duration / Map) or null. */
    Object value(Node operand, Facts facts) {
        if (operand instanceof Node.Literal l) return literalValue(l);
        if (!(operand instanceof Node.Ref ref)) return null;

        Object current = null;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < ref.segments().size(); i++) {
            Node.Segment seg = ref.segments().get(i);
            String key = path.isEmpty() ? seg.name() : path + "." + seg.name();
            if (seg.isCall()) {
                var fn = functions.find(key);
                if (fn.isEmpty()) return null;            // validator rejects unknown functions before publish
                List<Object> args = new ArrayList<>();
                for (Node arg : seg.args()) args.add(argumentValue(arg, facts));
                current = fn.get().apply(args, facts);
            } else if (i == 0) {
                current = facts.get(seg.name());
            } else if (current instanceof Map<?, ?> m) {
                current = m.get(seg.name());
            } else {
                return null;
            }
            if (current == null) return null;
            path.setLength(0);
            path.append(key);
        }
        return normalise(current);
    }

    /** A bare ref used as an argument (e.g. {@code person}) is passed as its value; unresolved roots pass their name. */
    private Object argumentValue(Node arg, Facts facts) {
        Object v = value(arg, facts);
        if (v == null && arg instanceof Node.Ref r && r.segments().size() == 1) return r.rootName();
        return v;
    }

    private static Object literalValue(Node.Literal l) {
        return l.value();
    }

    private static Object normalise(Object v) {
        return switch (v) {
            case BigDecimal b -> b;
            case Integer i -> BigDecimal.valueOf(i);
            case Long l -> BigDecimal.valueOf(l);
            case Double d -> BigDecimal.valueOf(d);
            case Float f -> BigDecimal.valueOf(f.doubleValue());
            case Number n -> new BigDecimal(n.toString());
            default -> v;
        };
    }

    private static boolean equalsLoose(Object a, Object b) {
        Integer cmp = compareValues(a, b);
        if (cmp != null) return cmp == 0;
        return a.toString().equals(b.toString());
    }

    /** Returns null when the two values are not orderable against each other. */
    private static Integer compareValues(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y);
        if (a instanceof Duration x && b instanceof Duration y) return x.compareTo(y);
        LocalDate da = asDate(a);
        LocalDate db = asDate(b);
        if (da != null && db != null && (a instanceof LocalDate || b instanceof LocalDate)) return da.compareTo(db);
        return null;
    }

    private static LocalDate asDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof String s && s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private String describe(Node operand) {
        return printer.operand(operand);
    }

    private static String show(Object v) {
        if (v == null) return "∅";
        if (v instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        return v.toString();
    }
}
