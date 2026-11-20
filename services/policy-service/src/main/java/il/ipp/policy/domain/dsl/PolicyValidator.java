package il.ipp.policy.domain.dsl;

import il.ipp.policy.domain.ast.Node;
import il.ipp.policy.domain.ast.PolicyDefinition;
import il.ipp.policy.domain.eval.FunctionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic checks that the grammar cannot express:
 * unknown fact roots, unknown functions, ordering comparisons between two symbols, etc.
 * Runs after a successful parse and before a policy can be saved or published.
 */
public final class PolicyValidator {

    /** Fact roots a policy may reference. Mirrors the {@code Facts} contract documented for callers. */
    public static final Set<String> KNOWN_ROOTS = Set.of(
            "person", "employer", "permit", "quotas", "sanctions", "form", "today", "vehicle", "trip", "crossing");

    private final FunctionRegistry functions;

    public PolicyValidator(FunctionRegistry functions) {
        this.functions = functions;
    }

    public List<SyntaxError> validate(PolicyDefinition p) {
        List<SyntaxError> errors = new ArrayList<>();
        if (p.appliesTo() != null) walk(p.appliesTo(), errors);
        walk(p.when(), errors);
        return errors;
    }

    /** Every fact path the policy reads, e.g. {@code person.age}. Shown in the Studio as "data this policy needs". */
    public Set<String> referencedPaths(PolicyDefinition p) {
        Set<String> out = new LinkedHashSet<>();
        collect(p.appliesTo(), out);
        collect(p.when(), out);
        return out;
    }

    private void walk(Node n, List<SyntaxError> errors) {
        switch (n) {
            case Node.And a -> { walk(a.left(), errors); walk(a.right(), errors); }
            case Node.Or o -> { walk(o.left(), errors); walk(o.right(), errors); }
            case Node.Not not -> walk(not.inner(), errors);
            case Node.Compare c -> {
                walk(c.left(), errors);
                walk(c.right(), errors);
                boolean ordering = c.op() != Node.CompareOp.EQ && c.op() != Node.CompareOp.NEQ;
                if (ordering && (isText(c.left()) || isText(c.right()))) {
                    errors.add(new SyntaxError(1, 0, "אי אפשר להשוות טקסט עם " + c.op().symbol()));
                }
            }
            case Node.In in -> walk(in.left(), errors);
            case Node.Truthy t -> walk(t.operand(), errors);
            case Node.Ref r -> checkRef(r, errors);
            case Node.Literal l -> { }
        }
    }

    private void checkRef(Node.Ref r, List<SyntaxError> errors) {
        Node.Segment first = r.segments().getFirst();
        if (!first.isCall() && !KNOWN_ROOTS.contains(first.name())) {
            errors.add(new SyntaxError(1, 0, "מקור נתונים לא מוכר: '" + first.name() + "'. מותר: " + KNOWN_ROOTS));
        }
        StringBuilder path = new StringBuilder();
        for (Node.Segment s : r.segments()) {
            String key = path.isEmpty() ? s.name() : path + "." + s.name();
            if (s.isCall()) {
                if (functions.find(key).isEmpty()) {
                    errors.add(new SyntaxError(1, 0, "פונקציה לא מוכרת: '" + key + "'. מותר: " + functions.names()));
                }
                for (Node arg : s.args()) {
                    if (arg instanceof Node.Ref argRef && argRef.segments().size() == 1
                            && KNOWN_ROOTS.contains(argRef.rootName())) continue;   // e.g. activeFor(person)
                    walk(arg, errors);
                }
            }
            path.setLength(0);
            path.append(key);
        }
    }

    private static boolean isText(Node n) {
        return n instanceof Node.Literal l
                && (l.type() == Node.LiteralType.STRING || l.type() == Node.LiteralType.SYMBOL);
    }

    private static void collect(Node n, Set<String> out) {
        if (n == null) return;
        switch (n) {
            case Node.And a -> { collect(a.left(), out); collect(a.right(), out); }
            case Node.Or o -> { collect(o.left(), out); collect(o.right(), out); }
            case Node.Not not -> collect(not.inner(), out);
            case Node.Compare c -> { collect(c.left(), out); collect(c.right(), out); }
            case Node.In in -> collect(in.left(), out);
            case Node.Truthy t -> collect(t.operand(), out);
            case Node.Ref r -> out.add(new AstPrinter().operand(r));
            case Node.Literal l -> { }
        }
    }
}
