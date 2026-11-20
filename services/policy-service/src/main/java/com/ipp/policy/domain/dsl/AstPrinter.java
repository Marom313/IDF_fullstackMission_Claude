package com.ipp.policy.domain.dsl;

import com.ipp.policy.domain.ast.Action;
import com.ipp.policy.domain.ast.Node;
import com.ipp.policy.domain.ast.PolicyDefinition;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Prints an AST back to DSL text in a canonical form (fixed keyword order, one clause per line,
 * minimal parentheses). Used for: version diffs that ignore whitespace noise, human-readable
 * explanations, and the round-trip test {@code parse(print(ast)) == ast}.
 */
public final class AstPrinter {

    public String print(PolicyDefinition p) {
        StringBuilder sb = new StringBuilder();
        sb.append("RULE ").append(quote(p.name())).append(" VERSION ").append(p.version());
        if (p.effectiveFrom() != null) sb.append(" EFFECTIVE ").append(p.effectiveFrom());
        sb.append('\n');
        if (p.appliesTo() != null) sb.append("APPLIES TO ").append(expr(p.appliesTo())).append('\n');
        if (!p.requiredFields().isEmpty()) {
            sb.append("REQUIRE FIELDS ").append(String.join(", ", p.requiredFields())).append('\n');
        }
        sb.append("WHEN ").append(expr(p.when())).append('\n');
        sb.append("THEN ").append(action(p.thenAction())).append('\n');
        sb.append("ELSE ").append(action(p.elseAction())).append('\n');
        return sb.toString();
    }

    public String action(Action a) {
        return switch (a.outcome()) {
            case APPROVE -> "APPROVE" + (a.validity() == null ? "" : " VALIDITY " + Durations.format(a.validity()));
            case REJECT -> "REJECT" + (a.reason() == null ? "" : " REASON " + quote(a.reason()));
            case REFER_TO_CLERK -> "REFER_TO_CLERK" + (a.reason() == null ? "" : " REASON " + quote(a.reason()));
        };
    }

    public String expr(Node n) {
        return expr(n, 0);
    }

    // precedence: OR=1, AND=2, NOT=3, atoms=4
    private String expr(Node n, int parentPrec) {
        int prec = precedence(n);
        String s = switch (n) {
            case Node.Or o -> expr(o.left(), prec) + " OR " + expr(o.right(), prec + 1);
            case Node.And a -> expr(a.left(), prec) + " AND " + expr(a.right(), prec + 1);
            case Node.Not not -> "NOT " + expr(not.inner(), prec);
            case Node.Compare c -> operand(c.left()) + " " + c.op().symbol() + " " + operand(c.right());
            case Node.In in -> operand(in.left()) + " IN [" +
                    in.values().stream().map(this::literal).collect(Collectors.joining(", ")) + "]";
            case Node.Truthy t -> operand(t.operand());
            case Node.Ref r -> operand(r);
            case Node.Literal l -> literal(l);
        };
        return prec < parentPrec ? "(" + s + ")" : s;
    }

    private static int precedence(Node n) {
        return switch (n) {
            case Node.Or o -> 1;
            case Node.And a -> 2;
            case Node.Not not -> 3;
            default -> 4;
        };
    }

    public String operand(Node n) {
        if (n instanceof Node.Literal l) return literal(l);
        if (n instanceof Node.Ref r) {
            return r.segments().stream().map(seg -> seg.isCall()
                    ? seg.name() + "(" + seg.args().stream().map(this::operand).collect(Collectors.joining(", ")) + ")"
                    : seg.name()).collect(Collectors.joining("."));
        }
        return "(" + expr(n) + ")";
    }

    public String literal(Node.Literal l) {
        return switch (l.type()) {
            case STRING -> quote((String) l.value());
            case NUMBER -> ((BigDecimal) l.value()).toPlainString();
            case BOOLEAN, SYMBOL, DATE -> l.value().toString();
            case DURATION -> Durations.format((Duration) l.value());
        };
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
