package com.ipp.policy.domain;

import com.ipp.policy.domain.ast.Action;
import com.ipp.policy.domain.ast.Node;
import com.ipp.policy.domain.ast.PolicyDefinition;
import com.ipp.policy.domain.dsl.AstCodec;
import com.ipp.policy.domain.dsl.AstPrinter;
import com.ipp.policy.domain.dsl.LineDiff;
import com.ipp.policy.domain.dsl.ParseResult;
import com.ipp.policy.domain.dsl.PolicyParser;
import com.ipp.policy.domain.dsl.PolicyValidator;
import com.ipp.policy.domain.eval.FunctionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.ipp.policy.domain.PolicyTestSupport.CONSTRUCTION;
import static com.ipp.policy.domain.PolicyTestSupport.parseOk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyParserTest {

    private final PolicyParser parser = new PolicyParser();
    private final PolicyValidator validator = new PolicyValidator(FunctionRegistry.standard());

    @Test
    void parsesFullPolicy() {
        PolicyDefinition p = parseOk(CONSTRUCTION);
        assertEquals("work-permit-construction", p.name());
        assertEquals(3, p.version());
        assertEquals(LocalDate.of(2026, 12, 1), p.effectiveFrom());
        assertEquals(List.of("idNumber", "photo", "employerId"), p.requiredFields());
        assertEquals(Action.Outcome.APPROVE, p.thenAction().outcome());
        assertEquals(Duration.ofDays(90), p.thenAction().validity());
        assertEquals(Action.Outcome.REFER_TO_CLERK, p.elseAction().outcome());
        assertEquals("תנאי זכאות לא מתקיימים", p.elseAction().reason());
        assertTrue(p.when() instanceof Node.And);
    }

    @Test
    void notBindsTighterThanAndWhichBindsTighterThanOr() {
        PolicyDefinition p = parseOk("""
                RULE "p" VERSION 1
                WHEN NOT person.a OR person.b AND person.c
                THEN APPROVE
                """);
        // NOT a OR (b AND c)
        Node.Or or = (Node.Or) p.when();
        assertTrue(or.left() instanceof Node.Not);
        assertTrue(or.right() instanceof Node.And);
    }

    @Test
    void missingElseDefaultsToReject() {
        PolicyDefinition p = parseOk("RULE \"p\" VERSION 1 WHEN person.ok THEN APPROVE");
        assertEquals(Action.Outcome.REJECT, p.elseAction().outcome());
    }

    @Test
    void reportsSyntaxErrorWithLineAndColumn() {
        ParseResult r = parser.parse("""
                RULE "p" VERSION 1
                WHEN person.age >=
                THEN APPROVE
                """);
        assertFalse(r.isValid());
        assertEquals(3, r.errors().getFirst().line());
    }

    @Test
    void rejectsEmptyAndHugeSources() {
        assertFalse(parser.parse("").isValid());
        assertFalse(parser.parse("x".repeat(PolicyParser.MAX_SOURCE_LENGTH + 1)).isValid());
    }

    @Test
    void duplicateRequiredFieldIsAnError() {
        assertFalse(parser.parse("RULE \"p\" VERSION 1 REQUIRE FIELDS photo, photo WHEN person.ok THEN APPROVE").isValid());
    }

    @Test
    void validatorRejectsUnknownFunctionsAndRoots() {
        PolicyDefinition p = parseOk("RULE \"p\" VERSION 1 WHEN hack(\"x\") AND secret.value == 1 THEN APPROVE");
        assertEquals(2, validator.validate(p).size());
    }

    @Test
    void validatorRejectsOrderingOnText() {
        PolicyDefinition p = parseOk("RULE \"p\" VERSION 1 WHEN person.securityStatus > CLEAR THEN APPROVE");
        assertEquals(1, validator.validate(p).size());
    }

    @Test
    void seedPoliciesAreValid() {
        for (String file : PolicyTestSupport.SEED_FILES) {
            PolicyDefinition p = parseOk(PolicyTestSupport.resource(file));
            assertEquals(List.of(), validator.validate(p), file);
        }
    }

    @Test
    void printThenParseIsIdentity() {
        AstPrinter printer = new AstPrinter();
        for (String file : PolicyTestSupport.SEED_FILES) {
            PolicyDefinition p = parseOk(PolicyTestSupport.resource(file));
            assertEquals(p, parseOk(printer.print(p)), file);
        }
        PolicyDefinition c = parseOk(CONSTRUCTION);
        assertEquals(c, parseOk(printer.print(c)));
    }

    @Test
    void codecRoundTrip() {
        AstCodec codec = new AstCodec();
        for (String file : PolicyTestSupport.SEED_FILES) {
            PolicyDefinition p = parseOk(PolicyTestSupport.resource(file));
            assertEquals(p, codec.decodePolicy(codec.encode(p)), file);
        }
    }

    @Test
    void canonicalDiffShowsOnlyRealChanges() {
        AstPrinter printer = new AstPrinter();
        String v1 = printer.print(parseOk("RULE \"p\" VERSION 1 WHEN person.age >= 22 THEN APPROVE VALIDITY 90d"));
        // same logic, different whitespace + one real change
        String v2 = printer.print(parseOk("RULE  \"p\"  VERSION 2\nWHEN   person.age >= 25\nTHEN APPROVE VALIDITY 90d"));
        List<LineDiff.Line> diff = LineDiff.diff(v1, v2);
        long changes = diff.stream().filter(l -> l.kind() != LineDiff.Kind.SAME).count();
        assertEquals(4, changes);   // header line and WHEN line: one removed + one added each
    }
}
