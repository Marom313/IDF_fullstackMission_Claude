package il.ipp.policy.domain;

import il.ipp.policy.domain.ast.PolicyDefinition;
import il.ipp.policy.domain.eval.Decision;
import il.ipp.policy.domain.eval.Evaluator;
import il.ipp.policy.domain.eval.Facts;
import il.ipp.policy.domain.eval.FunctionRegistry;
import il.ipp.policy.domain.eval.Simulator;
import il.ipp.policy.domain.eval.SyntheticCases;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static il.ipp.policy.domain.PolicyTestSupport.CONSTRUCTION;
import static il.ipp.policy.domain.PolicyTestSupport.goodWorker;
import static il.ipp.policy.domain.PolicyTestSupport.parseOk;
import static il.ipp.policy.domain.PolicyTestSupport.sub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    private final Evaluator evaluator = new Evaluator(FunctionRegistry.standard());
    private final PolicyDefinition construction = parseOk(CONSTRUCTION);

    private Decision run(Map<String, Object> data) {
        return evaluator.evaluate(construction, new Facts(data));
    }

    @Test
    void approvesEligibleWorker() {
        Decision d = run(goodWorker());
        assertEquals(Decision.Outcome.APPROVE, d.outcome());
        assertEquals(Duration.ofDays(90), d.validity());
        assertEquals(3, d.policyVersion());
        assertNotNull(d.trace());
    }

    @Test
    void tooYoungGoesToElseWithExplainableReason() {
        Map<String, Object> data = goodWorker();
        sub(data, "person").put("age", 19);
        Decision d = run(data);
        assertEquals(Decision.Outcome.REFER_TO_CLERK, d.outcome());
        assertEquals("תנאי זכאות לא מתקיימים", d.reasons().getFirst());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("person.age = 19")), d.reasons().toString());
    }

    @Test
    void activeSanctionBlocks() {
        Map<String, Object> data = goodWorker();
        sub(data, "sanctions").put("active", true);
        assertEquals(Decision.Outcome.REFER_TO_CLERK, run(data).outcome());
    }

    @Test
    void exhaustedQuotaBlocks() {
        Map<String, Object> data = goodWorker();
        sub(sub(data, "quotas"), "CONSTRUCTION").put("remaining", 0);
        Decision d = run(data);
        assertEquals(Decision.Outcome.REFER_TO_CLERK, d.outcome());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("quota(\"CONSTRUCTION\").remaining = 0")), d.reasons().toString());
    }

    @Test
    void missingFactIsUnknownAndNeverApproves() {
        Map<String, Object> data = goodWorker();
        sub(data, "person").remove("securityStatus");   // e.g. state registry was down
        Decision d = run(data);
        assertEquals(Decision.Outcome.REFER_TO_CLERK, d.outcome());
        assertEquals("חסר מידע להכרעה אוטומטית", d.reasons().getFirst());
    }

    @Test
    void unknownCombinedWithFalseIsFalse() {
        Map<String, Object> data = goodWorker();
        sub(data, "person").remove("securityStatus");
        sub(data, "person").put("age", 19);          // FALSE AND UNKNOWN = FALSE -> ELSE branch
        Decision d = run(data);
        assertEquals("תנאי זכאות לא מתקיימים", d.reasons().getFirst());
    }

    @Test
    void missingRequiredFieldIsIncomplete() {
        Map<String, Object> data = goodWorker();
        sub(data, "form").put("photo", "  ");
        Decision d = run(data);
        assertEquals(Decision.Outcome.INCOMPLETE, d.outcome());
        assertEquals(List.of("photo"), d.missingFields());
    }

    @Test
    void outOfScopeIsNotApplicable() {
        Map<String, Object> data = goodWorker();
        sub(data, "permit").put("type", "MEDICAL");
        assertEquals(Decision.Outcome.NOT_APPLICABLE, run(data).outcome());
    }

    @Test
    void inOperatorAllowsPendingSecurityForMedical() {
        PolicyDefinition medical = parseOk(PolicyTestSupport.resource("05-medical.ipp"));
        Map<String, Object> data = goodWorker();
        sub(data, "permit").put("type", "MEDICAL");
        sub(data, "person").put("securityStatus", "PENDING");
        sub(data, "form").putAll(Map.of("hospitalName", "HOSPITAL-1", "appointmentDate", "2026-11-25"));
        assertEquals(Decision.Outcome.APPROVE, evaluator.evaluate(medical, new Facts(data)).outcome());

        sub(data, "person").put("securityStatus", "DENIED");
        assertEquals(Decision.Outcome.REFER_TO_CLERK, evaluator.evaluate(medical, new Facts(data)).outcome());
    }

    @Test
    void quotaFunctionAcceptsAFactAsArgument() {
        PolicyDefinition general = parseOk(PolicyTestSupport.resource("03-work-general.ipp"));
        Map<String, Object> data = goodWorker();
        sub(data, "employer").put("sector", "INDUSTRY");
        data.put("quotas", new java.util.HashMap<>(Map.of("INDUSTRY", Map.of("remaining", 3))));
        assertEquals(Decision.Outcome.APPROVE, evaluator.evaluate(general, new Facts(data)).outcome());
        data.put("quotas", new java.util.HashMap<>(Map.of("INDUSTRY", Map.of("remaining", 0))));
        assertEquals(Decision.Outcome.REFER_TO_CLERK, evaluator.evaluate(general, new Facts(data)).outcome());
    }

    @Test
    void withoutFormRequiredFieldsAreNotChecked() {
        Map<String, Object> data = goodWorker();
        data.remove("form");                         // permit-service: eligibility-only evaluation
        assertEquals(Decision.Outcome.APPROVE, run(data).outcome());
    }

    @Test
    void numbersOfDifferentJavaTypesCompareCorrectly() {
        Map<String, Object> data = goodWorker();
        sub(data, "person").put("age", 22L);
        assertEquals(Decision.Outcome.APPROVE, run(data).outcome());
        sub(data, "person").put("age", 21.99d);
        assertEquals(Decision.Outcome.REFER_TO_CLERK, run(data).outcome());
    }

    @Test
    void simulationIsDeterministicAndDetectsChanges() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        List<Facts> cases = SyntheticCases.generate(1_000, 42L, today);
        assertEquals(cases, SyntheticCases.generate(1_000, 42L, today));

        PolicyDefinition v1 = parseOk(PolicyTestSupport.resource("01-work-construction.ipp"));
        PolicyDefinition stricter = parseOk(PolicyTestSupport.resource("01-work-construction.ipp")
                .replace("person.age >= 22", "person.age >= 30").replace("VERSION 1", "VERSION 2"));
        Simulator.Result r = new Simulator(evaluator).run(stricter, v1, cases);

        assertEquals(1_000, r.total());
        assertTrue(r.changed() > 0, "raising the minimum age must change some decisions");
        assertTrue(r.changedSamples().stream().allMatch(c ->
                c.before() == Decision.Outcome.APPROVE && c.after() == Decision.Outcome.REFER_TO_CLERK));
        // 1,000 evaluations x2 should take well under a second even on a laptop
        assertTrue(r.elapsedMicros() < 2_000_000, "simulation too slow: " + r.elapsedMicros() + "µs");
    }
}
