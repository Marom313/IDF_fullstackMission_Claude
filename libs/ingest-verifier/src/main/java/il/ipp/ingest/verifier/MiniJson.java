package il.ipp.ingest.verifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately tiny, strict JSON reader/writer, so the verifier needs nothing but the JDK.
 * Reads one flat object of strings / numbers / booleans / null (the manifest). Nested values are rejected:
 * a manifest with unexpected structure is a reason to quarantine, not to guess.
 */
final class MiniJson {

    private MiniJson() { }

    static Map<String, String> parseFlatObject(String text) {
        Parser p = new Parser(text);
        Map<String, String> out = p.object();
        p.ws();
        if (p.i != text.length()) throw new IllegalArgumentException("trailing characters after JSON object");
        return out;
    }

    /** Serialises a map of String / Number / Boolean / List&lt;String&gt; / Map (nested) values. */
    static String write(Map<String, ?> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':');
            value(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static void value(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> sb.append(quote(s));
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            case List<?> list -> {
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    value(sb, list.get(i));
                }
                sb.append(']');
            }
            case Map<?, ?> map -> sb.append(write((Map<String, ?>) map));
            default -> sb.append(quote(v.toString()));
        }
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end of JSON");
            return s.charAt(i);
        }

        void expect(char c) {
            ws();
            if (peek() != c) throw new IllegalArgumentException("expected '" + c + "' at " + i);
            i++;
        }

        Map<String, String> object() {
            Map<String, String> m = new LinkedHashMap<>();
            expect('{');
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String key = string();
                expect(':');
                ws();
                if (m.put(key, scalar()) != null) throw new IllegalArgumentException("duplicate key " + key);
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("expected ',' or '}' at " + (i - 1));
            }
        }

        String scalar() {
            char c = peek();
            if (c == '"') return string();
            if (c == '{' || c == '[') throw new IllegalArgumentException("nested values are not allowed in a manifest");
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
            String raw = s.substring(start, i);
            if (raw.equals("null")) return null;
            if (raw.equals("true") || raw.equals("false") || raw.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) return raw;
            throw new IllegalArgumentException("invalid literal '" + raw + "'");
        }

        String string() {
            if (peek() != '"') throw new IllegalArgumentException("expected string at " + i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = peek();
                    i++;
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    if (c < 0x20) throw new IllegalArgumentException("control character in string");
                    sb.append(c);
                }
            }
        }
    }
}
