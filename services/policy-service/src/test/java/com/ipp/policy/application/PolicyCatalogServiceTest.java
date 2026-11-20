package com.ipp.policy.application;

import com.ipp.policy.domain.PolicyTestSupport;
import com.ipp.policy.domain.dsl.LineDiff;
import com.ipp.policy.domain.dsl.PolicyParser;
import com.ipp.policy.domain.dsl.PolicyValidator;
import com.ipp.policy.domain.eval.Decision;
import com.ipp.policy.domain.eval.Evaluator;
import com.ipp.policy.domain.eval.Facts;
import com.ipp.policy.domain.eval.FunctionRegistry;
import com.ipp.policy.domain.eval.Simulator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ipp.policy.domain.PolicyTestSupport.goodWorker;
import static com.ipp.policy.domain.PolicyTestSupport.resource;
import static com.ipp.policy.domain.PolicyTestSupport.sub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyCatalogServiceTest {

    private final InMemoryPolicyRepository repo = new InMemoryPolicyRepository();
    private final List<String> published = new ArrayList<>();
    private final PolicyCatalogService service;

    PolicyCatalogServiceTest() {
        FunctionRegistry fns = FunctionRegistry.standard();
        Evaluator evaluator = new Evaluator(fns);
        service = new PolicyCatalogService(repo,
                (activated, retired) -> published.add(activated.name() + " v" + activated.version()),
                new PolicyParser(), new PolicyValidator(fns), evaluator, new Simulator(evaluator),
                Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Jerusalem")));
    }

    private void seedAll() {
        for (String f : PolicyTestSupport.SEED_FILES) {
            try {
                PolicyRecord r = service.createDraft(resource(f), "test");
                service.publish(r.name(), r.version(), "test");
            } catch (PolicyErrors.Conflict alreadyThere) {
                // same as SeedPolicies on startup
            }
        }
    }

    @Test
    void draftThenPublishActivatesAndEmitsEvent() {
        PolicyRecord draft = service.createDraft(resource("01-work-construction.ipp"), "alice");
        assertEquals(PolicyStatus.DRAFT, draft.status());
        assertEquals(64, draft.sha256().length());
        assertEquals(0, service.activeCount());

        PolicyRecord active = service.publish(draft.name(), draft.version(), "bob");
        assertEquals(PolicyStatus.ACTIVE, active.status());
        assertEquals("bob", active.publishedBy());
        assertEquals(1, service.activeCount());
        assertEquals(List.of("work-permit-construction v1"), published);

        // idempotent
        service.publish(draft.name(), draft.version(), "bob");
        assertEquals(1, published.size());
    }

    @Test
    void publishingNewVersionRetiresOldOne() {
        String v1 = resource("01-work-construction.ipp");
        service.publish("work-permit-construction", service.createDraft(v1, "a").version(), "a");
        String v2 = v1.replace("VERSION 1", "VERSION 2").replace("person.age >= 22", "person.age >= 23");
        service.publish("work-permit-construction", service.createDraft(v2, "a").version(), "a");

        List<PolicyRecord> versions = service.versions("work-permit-construction");
        assertEquals(2, versions.getFirst().version());
        assertEquals(PolicyStatus.ACTIVE, versions.get(0).status());
        assertEquals(PolicyStatus.RETIRED, versions.get(1).status());
        assertEquals(1, service.activeCount());

        List<LineDiff.Line> diff = service.diff("work-permit-construction", 1, 2);
        assertTrue(diff.stream().anyMatch(l -> l.kind() == LineDiff.Kind.ADDED && l.text().contains(">= 23")));
        assertThrows(PolicyErrors.Conflict.class, () -> service.publish("work-permit-construction", 1, "a"));
    }

    @Test
    void versionMustIncrease() {
        service.createDraft(resource("01-work-construction.ipp").replace("VERSION 1", "VERSION 5"), "a");
        assertThrows(PolicyErrors.Conflict.class, () -> service.createDraft(resource("01-work-construction.ipp"), "a"));
    }

    @Test
    void invalidSourceIsRejectedWithErrors() {
        PolicyErrors.InvalidPolicy ex = assertThrows(PolicyErrors.InvalidPolicy.class,
                () -> service.createDraft("RULE \"x\" VERSION 1 WHEN evil() THEN APPROVE", "a"));
        assertFalse(ex.errors().isEmpty());
        assertFalse(service.validate("RULE").valid());
        assertTrue(service.validate(resource("03-driver-transport.ipp")).valid());
    }

    @Test
    void evaluateRoutesToMatchingPolicy() {
        seedAll();
        Map<String, Object> worker = goodWorker();
        PolicyCatalogService.Evaluation e = service.evaluate(new Facts(worker));
        assertEquals(Decision.Outcome.APPROVE, e.decision().outcome());
        assertEquals("work-permit-construction", e.decision().policyName());
        assertEquals(1, e.applicable().size());
    }

    @Test
    void noMatchingPolicyIsReferredNotApproved() {
        seedAll();
        Map<String, Object> data = goodWorker();
        sub(data, "permit").put("type", "UNKNOWN_TYPE");
        PolicyCatalogService.Evaluation e = service.evaluate(new Facts(data));
        assertEquals(Decision.Outcome.REFER_TO_CLERK, e.decision().outcome());
        assertNull(e.decision().policyName());
    }

    @Test
    void requiredFieldsForPartialForm() {
        seedAll();
        // only permit type known: both WORK_ENTRY policies may apply, sector unknown
        List<String> fields = service.requiredFields(new Facts(Map.of("permit", Map.of("type", "WORK_ENTRY"))));
        assertEquals(List.of("idNumber", "photo", "magneticCard", "employerId", "workRegion"), fields);

        List<String> driver = service.requiredFields(new Facts(Map.of("permit", Map.of("type", "DRIVER"))));
        assertTrue(driver.contains("vehiclePlate"));
        assertFalse(driver.contains("employerId"));
    }

    /** The exact nested facts permit-service on main builds (PermitService.facts). */
    @Test
    void factsAsSentByPermitServiceOnMain() {
        seedAll();
        Map<String, Object> facts = new java.util.HashMap<>();
        facts.put("person", Map.of("age", 34, "securityStatus", "CLEAR", "region", "CENTER", "magneticCardValid", true));
        facts.put("sanctions", Map.of("active", false));
        facts.put("today", "2026-11-20");
        facts.put("quotas", Map.of("CONSTRUCTION", Map.of("remaining", 10, "total", 100), "SERVICES", Map.of("remaining", 3, "total", 50)));
        facts.put("employer", Map.of("sector", "SERVICES"));

        facts.put("permit", Map.of("type", "WORK_ENTRY"));
        facts.put("form", Map.of("idNumber", "980000123", "photo", "s3://p", "magneticCard", "MC-1", "employerId", "E", "workRegion", "CENTER"));
        assertEquals("work-permit-general", service.evaluate(new Facts(facts)).decision().policyName());
        assertEquals(Decision.Outcome.APPROVE, service.evaluate(new Facts(facts)).decision().outcome());

        facts.put("permit", Map.of("type", "DRIVER"));
        facts.put("form", Map.of("idNumber", "980000123", "photo", "s3://p", "magneticCard", "MC-1",
                "vehiclePlate", "12-345-67", "cargoType", "FOOD", "destinationRegion", "CENTER"));
        Decision driver = service.evaluate(new Facts(facts)).decision();
        assertEquals("driver-transport", driver.policyName());
        assertEquals(2, driver.policyVersion());
        assertEquals(Decision.Outcome.APPROVE, driver.outcome());

        facts.put("permit", Map.of("type", "MEDICAL"));
        facts.put("person", Map.of("age", 60, "securityStatus", "PENDING", "magneticCardValid", false));
        facts.put("form", Map.of("idNumber", "980000123", "photo", "s3://p", "hospitalName", "H-1", "appointmentDate", "2026-11-25"));
        assertEquals(Decision.Outcome.APPROVE, service.evaluate(new Facts(facts)).decision().outcome());
    }

    @Test
    void reseedingBumpedVersionsRetiresV1() {
        // simulates an existing Mongo volume that already holds driver-transport v1
        String v1 = PolicyTestSupport.resource("03-driver-transport.ipp").replace("VERSION 2", "VERSION 1")
                .replace("permit.type == DRIVER", "permit.type == DRIVER_TRANSPORT");
        service.publish("driver-transport", service.createDraft(v1, "old").version(), "old");
        seedAll();
        List<PolicyRecord> versions = service.versions("driver-transport");
        assertEquals(PolicyStatus.ACTIVE, versions.get(0).status());
        assertEquals(2, versions.get(0).version());
        assertEquals(PolicyStatus.RETIRED, versions.get(1).status());
    }

    @Test
    void simulationComparesDraftWithActive() {
        seedAll();
        String draft = resource("02-work-agriculture.ipp").replace("VERSION 1", "VERSION 2")
                .replace("person.age >= 18", "person.age >= 40");
        Simulator.Result r = service.simulate(draft, 2_000, 7L);
        assertEquals(2_000, r.total());
        assertTrue(r.changed() > 0);
        assertFalse(r.baselineCounts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.simulate(draft, 0, 1L));
    }
}
