package il.ipp.policy.adapters.web;

import il.ipp.policy.application.FlatFacts;
import il.ipp.policy.application.PolicyCatalogService;
import il.ipp.policy.domain.eval.Decision;
import il.ipp.policy.domain.eval.Facts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service-to-service contract used by permit-service ({@code PolicyHttpAdapter} on main).
 * Not routed by the gateway (only {@code /api/policies/**} is), so it is reachable only inside the compose network.
 * <p>
 * Request:  {@code {"permitType":"WORK_ENTRY","facts":{"person.age":31,"employer.sector":"CONSTRUCTION",...}}}
 * Response: {@code {"decision":"APPROVE","reasons":[...],"policyVersion":"work-permit-construction@v1","validityDays":90}}
 */
@RestController
@RequestMapping("/internal/policies")
@Validated
class InternalPolicyController {

    private final PolicyCatalogService service;

    InternalPolicyController(PolicyCatalogService service) {
        this.service = service;
    }

    record EvaluateRequest(@NotBlank @Pattern(regexp = "[A-Z_]{2,40}") String permitType, Map<String, Object> facts) { }

    record EvaluateResponse(String decision, List<String> reasons, String policyVersion, Integer validityDays) { }

    record RequiredFieldsResponse(List<String> requiredFields) { }

    @PostMapping("/evaluate")
    EvaluateResponse evaluate(@Valid @RequestBody EvaluateRequest body) {
        Decision d = service.evaluate(FlatFacts.toFacts(body.permitType(), body.facts())).decision();
        // main knows three outcomes; INCOMPLETE / NOT_APPLICABLE are not auto-decidable → clerk
        String decision = switch (d.outcome()) {
            case APPROVE -> "APPROVE";
            case REJECT -> "REJECT";
            case REFER_TO_CLERK, INCOMPLETE, NOT_APPLICABLE -> "REFER_TO_CLERK";
        };
        String version = d.policyName() == null ? "none" : d.policyName() + "@v" + d.policyVersion();
        Integer days = d.validity() == null ? null : (int) d.validity().toDays();
        return new EvaluateResponse(decision, d.reasons(), version, days);
    }

    @GetMapping("/required-fields")
    RequiredFieldsResponse requiredFields(@RequestParam @Pattern(regexp = "[A-Z_]{2,40}") String permitType) {
        Facts partial = FlatFacts.toFacts(permitType, Map.of());
        return new RequiredFieldsResponse(service.requiredFields(partial));
    }
}
