package com.ipp.policy.domain.ast;

import java.util.List;
import java.util.Objects;

/**
 * Abstract syntax tree of a policy condition.
 * <p>
 * Pure domain model: no Spring, no Jackson annotations. Serialisation to JSON/Mongo is done by
 * {@link com.ipp.policy.domain.dsl.AstCodec} so the domain stays framework-free (enforced by ArchUnit).
 */
public sealed interface Node permits Node.And, Node.Or, Node.Not, Node.Compare, Node.In, Node.Truthy, Node.Ref, Node.Literal {

    record And(Node left, Node right) implements Node { }

    record Or(Node left, Node right) implements Node { }

    record Not(Node inner) implements Node { }

    record Compare(CompareOp op, Node left, Node right) implements Node { }

    record In(Node left, List<Literal> values) implements Node {
        public In { values = List.copyOf(values); }
    }

    /** A bare operand used as a boolean, e.g. {@code sanctions.activeFor(person)}. */
    record Truthy(Node operand) implements Node { }

    /** A fact path, e.g. {@code person.age} or {@code quota("CONSTRUCTION").remaining}. */
    record Ref(List<Segment> segments) implements Node {
        public Ref { segments = List.copyOf(segments); }

        public String rootName() { return segments.getFirst().name(); }
    }

    /** One step of a path. {@code args == null} means "property access", a list (maybe empty) means "function call". */
    record Segment(String name, List<Node> args) {
        public Segment {
            Objects.requireNonNull(name);
            args = args == null ? null : List.copyOf(args);
        }

        public boolean isCall() { return args != null; }
    }

    record Literal(LiteralType type, Object value) implements Node {
        public Literal {
            Objects.requireNonNull(type);
            Objects.requireNonNull(value);
        }
    }

    enum LiteralType { STRING, NUMBER, BOOLEAN, DATE, DURATION, SYMBOL }

    enum CompareOp {
        EQ("=="), NEQ("!="), GTE(">="), LTE("<="), GT(">"), LT("<");

        private final String symbol;

        CompareOp(String symbol) { this.symbol = symbol; }

        public String symbol() { return symbol; }

        public static CompareOp fromSymbol(String s) {
            for (CompareOp op : values()) {
                if (op.symbol.equals(s)) return op;
            }
            throw new IllegalArgumentException("Unknown operator: " + s);
        }
    }
}
