package com.ipp.policy.domain.dsl;

import com.ipp.policy.domain.ast.Action;
import com.ipp.policy.domain.ast.Node;
import com.ipp.policy.domain.ast.PolicyDefinition;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the AST to/from plain {@code Map/List/String/Number/Boolean} structures.
 * <p>
 * That shape is stored in Mongo as a native document (queryable, human readable) and returned by the
 * REST API as JSON, without putting Jackson or Spring Data annotations on domain classes.
 * Every node carries a {@code "kind"} discriminator.
 */
public final class AstCodec {

    // ------------------------------------------------------------------ encode

    public Map<String, Object> encode(PolicyDefinition p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("version", p.version());
        m.put("effectiveFrom", p.effectiveFrom() == null ? null : p.effectiveFrom().toString());
        m.put("appliesTo", p.appliesTo() == null ? null : encode(p.appliesTo()));
        m.put("requiredFields", p.requiredFields());
        m.put("when", encode(p.when()));
        m.put("then", encode(p.thenAction()));
        m.put("else", encode(p.elseAction()));
        return m;
    }

    public Map<String, Object> encode(Action a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", a.outcome().name());
        m.put("validityMinutes", a.validity() == null ? null : a.validity().toMinutes());
        m.put("reason", a.reason());
        return m;
    }

    public Map<String, Object> encode(Node n) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (n) {
            case Node.And a -> { m.put("kind", "and"); m.put("left", encode(a.left())); m.put("right", encode(a.right())); }
            case Node.Or o -> { m.put("kind", "or"); m.put("left", encode(o.left())); m.put("right", encode(o.right())); }
            case Node.Not not -> { m.put("kind", "not"); m.put("inner", encode(not.inner())); }
            case Node.Compare c -> {
                m.put("kind", "compare"); m.put("op", c.op().name());
                m.put("left", encode(c.left())); m.put("right", encode(c.right()));
            }
            case Node.In in -> {
                m.put("kind", "in"); m.put("left", encode(in.left()));
                m.put("values", in.values().stream().map(this::encode).toList());
            }
            case Node.Truthy t -> { m.put("kind", "truthy"); m.put("operand", encode(t.operand())); }
            case Node.Ref r -> {
                m.put("kind", "ref");
                List<Map<String, Object>> segs = new ArrayList<>();
                for (Node.Segment s : r.segments()) {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("name", s.name());
                    sm.put("args", s.args() == null ? null : s.args().stream().map(this::encode).toList());
                    segs.add(sm);
                }
                m.put("segments", segs);
            }
            case Node.Literal l -> {
                m.put("kind", "literal"); m.put("type", l.type().name());
                m.put("value", switch (l.type()) {
                    case NUMBER -> ((BigDecimal) l.value()).toPlainString();   // string: no float precision loss
                    case DURATION -> ((Duration) l.value()).toMinutes();
                    case DATE -> l.value().toString();
                    default -> l.value();
                });
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ decode

    @SuppressWarnings("unchecked")
    public PolicyDefinition decodePolicy(Map<String, Object> m) {
        List<String> fields = m.get("requiredFields") == null ? List.of() : List.copyOf((List<String>) m.get("requiredFields"));
        return new PolicyDefinition(
                (String) m.get("name"),
                ((Number) m.get("version")).intValue(),
                m.get("effectiveFrom") == null ? null : LocalDate.parse((String) m.get("effectiveFrom")),
                m.get("appliesTo") == null ? null : decode((Map<String, Object>) m.get("appliesTo")),
                fields,
                decode((Map<String, Object>) m.get("when")),
                decodeAction((Map<String, Object>) m.get("then")),
                m.get("else") == null ? null : decodeAction((Map<String, Object>) m.get("else")));
    }

    public Action decodeAction(Map<String, Object> m) {
        Object minutes = m.get("validityMinutes");
        return new Action(Action.Outcome.valueOf((String) m.get("outcome")),
                minutes == null ? null : Duration.ofMinutes(((Number) minutes).longValue()),
                (String) m.get("reason"));
    }

    @SuppressWarnings("unchecked")
    public Node decode(Map<String, Object> m) {
        String kind = (String) m.get("kind");
        return switch (kind) {
            case "and" -> new Node.And(decode(map(m, "left")), decode(map(m, "right")));
            case "or" -> new Node.Or(decode(map(m, "left")), decode(map(m, "right")));
            case "not" -> new Node.Not(decode(map(m, "inner")));
            case "compare" -> new Node.Compare(Node.CompareOp.valueOf((String) m.get("op")),
                    decode(map(m, "left")), decode(map(m, "right")));
            case "in" -> new Node.In(decode(map(m, "left")),
                    ((List<Map<String, Object>>) m.get("values")).stream().map(v -> (Node.Literal) decode(v)).toList());
            case "truthy" -> new Node.Truthy(decode(map(m, "operand")));
            case "ref" -> new Node.Ref(((List<Map<String, Object>>) m.get("segments")).stream()
                    .map(s -> new Node.Segment((String) s.get("name"),
                            s.get("args") == null ? null
                                    : ((List<Map<String, Object>>) s.get("args")).stream().map(this::decode).toList()))
                    .toList());
            case "literal" -> {
                Node.LiteralType type = Node.LiteralType.valueOf((String) m.get("type"));
                Object v = m.get("value");
                yield new Node.Literal(type, switch (type) {
                    case NUMBER -> new BigDecimal(v.toString());
                    case DURATION -> Duration.ofMinutes(((Number) v).longValue());
                    case DATE -> LocalDate.parse(v.toString());
                    case BOOLEAN -> Boolean.valueOf(v.toString());
                    default -> v.toString();
                });
            }
            default -> throw new IllegalArgumentException("Unknown AST node kind: " + kind);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }
}
