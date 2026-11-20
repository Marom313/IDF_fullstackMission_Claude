package il.ipp.policy.domain.dsl;

import il.ipp.policy.domain.ast.Action;
import il.ipp.policy.domain.ast.Node;
import il.ipp.policy.domain.ast.PolicyDefinition;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns DSL source text into a {@link PolicyDefinition}.
 * <p>
 * Never throws on bad input: every lexer, parser and semantic problem is collected as a {@link SyntaxError}
 * with line/column, so the Policy Studio editor can underline it.
 */
public final class PolicyParser {

    /** Hard limit to protect the service from huge payloads (DoS). */
    public static final int MAX_SOURCE_LENGTH = 20_000;

    public ParseResult parse(String source) {
        List<SyntaxError> errors = new ArrayList<>();
        if (source == null || source.isBlank()) {
            errors.add(new SyntaxError(1, 0, "המדיניות ריקה"));
            return ParseResult.failure(errors);
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            errors.add(new SyntaxError(1, 0, "המדיניות ארוכה מדי (מקסימום " + MAX_SOURCE_LENGTH + " תווים)"));
            return ParseResult.failure(errors);
        }

        BaseErrorListener listener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add(new SyntaxError(line, charPositionInLine, msg));
            }
        };

        PolicyDslLexer lexer = new PolicyDslLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        PolicyDslParser parser = new PolicyDslParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        PolicyDslParser.PolicyContext tree = parser.policy();
        if (!errors.isEmpty()) {
            return ParseResult.failure(errors);
        }
        try {
            AstBuilder builder = new AstBuilder(errors);
            PolicyDefinition definition = builder.build(tree);
            return errors.isEmpty() ? ParseResult.success(definition) : ParseResult.failure(errors);
        } catch (RuntimeException ex) {
            errors.add(new SyntaxError(1, 0, "שגיאה בבניית המדיניות: " + ex.getMessage()));
            return ParseResult.failure(errors);
        }
    }

    /** Walks the ANTLR parse tree and builds our own immutable AST (decoupled from ANTLR classes). */
    private static final class AstBuilder {
        private final List<SyntaxError> errors;

        AstBuilder(List<SyntaxError> errors) { this.errors = errors; }

        PolicyDefinition build(PolicyDslParser.PolicyContext ctx) {
            String name = unquote(ctx.name.getText());
            int version = parseVersion(ctx.version);
            LocalDate effective = ctx.effective == null ? null : parseDate(ctx.effective);
            Node appliesTo = ctx.appliesTo() == null ? null : expr(ctx.appliesTo().expr());
            List<String> fields = new ArrayList<>();
            if (ctx.requireFields() != null) {
                for (TerminalNode id : ctx.requireFields().IDENT()) {
                    String f = id.getText();
                    if (fields.contains(f)) {
                        errors.add(err(id.getSymbol(), "השדה '" + f + "' מופיע פעמיים ב-REQUIRE FIELDS"));
                    }
                    fields.add(f);
                }
            }
            Node when = expr(ctx.condition);
            Action thenAction = action(ctx.thenAction);
            Action elseAction = ctx.elseAction == null ? null : action(ctx.elseAction);
            if (name.isBlank()) {
                errors.add(err(ctx.name, "שם המדיניות לא יכול להיות ריק"));
            }
            return new PolicyDefinition(name, version, effective, appliesTo, fields, when, thenAction, elseAction);
        }

        private int parseVersion(Token t) {
            try {
                int v = Integer.parseInt(t.getText());
                if (v < 1) errors.add(err(t, "VERSION חייב להיות 1 ומעלה"));
                return v;
            } catch (NumberFormatException e) {
                errors.add(err(t, "VERSION חייב להיות מספר שלם"));
                return 0;
            }
        }

        private LocalDate parseDate(Token t) {
            try {
                return LocalDate.parse(t.getText());
            } catch (DateTimeParseException e) {
                errors.add(err(t, "תאריך לא תקין: " + t.getText()));
                return null;
            }
        }

        private Action action(PolicyDslParser.ActionContext ctx) {
            if (ctx instanceof PolicyDslParser.ApproveActionContext a) {
                return Action.approve(a.DURATION() == null ? null : parseDuration(a.DURATION().getSymbol()));
            }
            if (ctx instanceof PolicyDslParser.RejectActionContext r) {
                return Action.reject(r.STRING() == null ? null : unquote(r.STRING().getText()));
            }
            PolicyDslParser.ReferActionContext f = (PolicyDslParser.ReferActionContext) ctx;
            return Action.refer(f.STRING() == null ? null : unquote(f.STRING().getText()));
        }

        private Node expr(PolicyDslParser.ExprContext ctx) {
            return switch (ctx) {
                case PolicyDslParser.NotExprContext n -> new Node.Not(expr(n.expr()));
                case PolicyDslParser.AndExprContext a -> new Node.And(expr(a.expr(0)), expr(a.expr(1)));
                case PolicyDslParser.OrExprContext o -> new Node.Or(expr(o.expr(0)), expr(o.expr(1)));
                case PolicyDslParser.ParenExprContext p -> expr(p.expr());
                case PolicyDslParser.InExprContext i -> new Node.In(operand(i.operand()),
                        i.literal().stream().map(this::literal).toList());
                case PolicyDslParser.CompareExprContext c -> new Node.Compare(
                        Node.CompareOp.fromSymbol(c.compOp().getText()), operand(c.operand(0)), operand(c.operand(1)));
                case PolicyDslParser.TruthyExprContext t -> new Node.Truthy(operand(t.operand()));
                default -> throw new IllegalStateException("Unsupported expression: " + ctx.getText());
            };
        }

        private Node operand(PolicyDslParser.OperandContext ctx) {
            if (ctx instanceof PolicyDslParser.LiteralOperandContext l) {
                return literal(l.literal());
            }
            PolicyDslParser.RefContext ref = ((PolicyDslParser.RefOperandContext) ctx).ref();
            List<Node.Segment> segments = new ArrayList<>();
            for (PolicyDslParser.SegmentContext s : ref.segment()) {
                List<Node> args = null;
                if (s.LPAREN() != null) {
                    args = s.operand().stream().map(this::operand).toList();
                }
                segments.add(new Node.Segment(s.IDENT().getText(), args));
            }
            return new Node.Ref(segments);
        }

        private Node.Literal literal(PolicyDslParser.LiteralContext ctx) {
            return switch (ctx) {
                case PolicyDslParser.StringLitContext s -> new Node.Literal(Node.LiteralType.STRING, unquote(s.getText()));
                case PolicyDslParser.NumberLitContext n -> new Node.Literal(Node.LiteralType.NUMBER, new BigDecimal(n.getText()));
                case PolicyDslParser.BoolLitContext b -> new Node.Literal(Node.LiteralType.BOOLEAN, Boolean.parseBoolean(b.getText()));
                case PolicyDslParser.DateLitContext d -> {
                    LocalDate date = parseDate(d.DATE().getSymbol());
                    yield new Node.Literal(Node.LiteralType.DATE, date == null ? LocalDate.EPOCH : date);
                }
                case PolicyDslParser.DurationLitContext d -> new Node.Literal(Node.LiteralType.DURATION, parseDuration(d.DURATION().getSymbol()));
                case PolicyDslParser.SymbolLitContext s -> new Node.Literal(Node.LiteralType.SYMBOL, s.getText());
                default -> throw new IllegalStateException("Unsupported literal: " + ctx.getText());
            };
        }

        private Duration parseDuration(Token t) {
            return Durations.parse(t.getText());
        }

        private static SyntaxError err(Token t, String message) {
            return new SyntaxError(t.getLine(), t.getCharPositionInLine(), message);
        }

        private static String unquote(String quoted) {
            String body = quoted.substring(1, quoted.length() - 1);
            return body.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
