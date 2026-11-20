package com.ipp.policy.adapters.web;

import com.ipp.policy.application.PolicyRecord;
import com.ipp.policy.application.PolicyStatus;
import com.ipp.policy.domain.eval.Decision;
import com.ipp.policy.domain.eval.Trace;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request/response bodies. Kept separate from domain types so the API contract can evolve independently. */
public final class PolicyDtos {

    private PolicyDtos() { }

    public record SourceRequest(@NotBlank @Size(max = 20_000) String source) { }

    public record SimulateRequest(@NotBlank @Size(max = 20_000) String source,
                                  @Min(1) @Max(10_000) Integer caseCount,
                                  Long seed) { }

    public record FactsRequest(@NotNull Map<String, Object> facts) { }

    public record PolicySummary(String name, int version, PolicyStatus status, LocalDate effectiveFrom,
                                List<String> requiredFields, String sha256, String createdBy, Instant createdAt,
                                String publishedBy, Instant publishedAt) {
        static PolicySummary of(PolicyRecord r) {
            return new PolicySummary(r.name(), r.version(), r.status(), r.effectiveFrom(), r.requiredFields(), r.sha256(),
                    r.createdBy(), r.createdAt(), r.publishedBy(), r.publishedAt());
        }
    }

    public record PolicyDetail(PolicySummary summary, String source, String canonicalSource, Map<String, Object> ast) {
        static PolicyDetail of(PolicyRecord r) {
            return new PolicyDetail(PolicySummary.of(r), r.source(), r.canonicalSource(), r.ast());
        }
    }

    /** Wire format of a decision. {@code validityDays} instead of ISO durations to keep the UI simple. */
    public record DecisionDto(Decision.Outcome outcome, String policyName, int policyVersion, Long validityDays,
                              List<String> missingFields, List<String> reasons, Trace trace) {
        static DecisionDto of(Decision d) {
            return new DecisionDto(d.outcome(), d.policyName(), d.policyVersion(),
                    d.validity() == null ? null : d.validity().toDays(), d.missingFields(), d.reasons(), d.trace());
        }
    }

    public record EvaluationResponse(DecisionDto decision, List<DecisionDto> applicable, Instant checkedAt) { }

    public record RequiredFieldsResponse(List<String> requiredFields) { }
}
