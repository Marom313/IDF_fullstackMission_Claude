package com.ipp.policy.domain.eval;

import java.util.List;

/**
 * Explanation tree: one node per evaluated expression, with the values that were compared.
 * The clerk UI renders this so every decision is explainable ("why was this rejected?").
 *
 * @param expression canonical DSL text of the sub-expression
 * @param result     TRUE / FALSE / UNKNOWN
 * @param detail     e.g. {@code "person.age = 19, נדרש >= 22"} for comparisons, null for boolean nodes
 */
public record Trace(String expression, Tri result, String detail, List<Trace> children) {

    public Trace {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static Trace leaf(String expression, Tri result, String detail) {
        return new Trace(expression, result, detail, List.of());
    }

    /** Leaf comparisons that evaluated to the given result (used to build the human "reasons" list). */
    public List<Trace> leavesWith(Tri wanted) {
        if (children.isEmpty()) return result == wanted ? List.of(this) : List.of();
        return children.stream().flatMap(c -> c.leavesWith(wanted).stream()).toList();
    }
}
