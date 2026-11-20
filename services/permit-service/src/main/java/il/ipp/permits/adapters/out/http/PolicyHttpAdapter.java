package il.ipp.permits.adapters.out.http;

import il.ipp.permits.domain.PermitPorts.PolicyDecision;
import il.ipp.permits.domain.PermitPorts.PolicyEvaluator;
import il.ipp.permits.domain.PermitType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter to policy-service (ANTLR DSL evaluator, built on branch cloud/policy-and-mock).
 * Contract: POST /api/policies/evaluate {facts:{person:{..},employer:{..},permit:{type},quotas:{..},form:{..}}}
 *        → {decision:{outcome, policyName, policyVersion, validityDays, reasons[], missingFields[]}}
 * Circuit breaker + retry with jitter + short timeouts; the caller's JWT is forwarded.
 * Fallback: REFER_TO_CLERK with an explicit reason (fail-safe, never auto-approve when policy is unknown).
 */
@Component
class PolicyHttpAdapter implements PolicyEvaluator {
  private static final Logger log = LoggerFactory.getLogger(PolicyHttpAdapter.class);
  private final RestClient http;

  PolicyHttpAdapter(@Value("${ipp.policy.url:http://policy-service:8080}") String baseUrl) {
    var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    rf.setConnectTimeout(Duration.ofSeconds(1));
    rf.setReadTimeout(Duration.ofSeconds(2));
    this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf)
        .requestInterceptor(BearerForwarding.INSTANCE).build();
  }

  record FactsRequest(Map<String, Object> facts) {}

  record DecisionDto(String outcome, String policyName, Integer policyVersion, Long validityDays,
                     List<String> missingFields, List<String> reasons) {}

  record EvaluationResponse(DecisionDto decision) {}

  record RequiredFieldsResponse(List<String> requiredFields) {}

  @Override
  @CircuitBreaker(name = "policy", fallbackMethod = "evaluateFallback")
  @Retry(name = "policy")
  public PolicyDecision evaluate(PermitType type, Map<String, Object> facts) {
    EvaluationResponse r = http.post().uri("/api/policies/evaluate").body(new FactsRequest(facts)).retrieve()
        .body(EvaluationResponse.class);
    if (r == null || r.decision() == null) {
      throw new IllegalStateException("empty policy response");
    }
    DecisionDto d = r.decision();
    List<String> reasons = new ArrayList<>(d.reasons() == null ? List.of() : d.reasons());
    if (d.missingFields() != null && !d.missingFields().isEmpty()) {
      reasons.add("MISSING_FIELDS:" + String.join(",", d.missingFields()));
    }
    String outcome = switch (d.outcome()) {
      case "APPROVE" -> "APPROVE";
      case "REJECT" -> "REJECT";
      case "NOT_APPLICABLE" -> {
        reasons.add("NO_APPLICABLE_POLICY");
        yield "REFER_TO_CLERK";
      }
      default -> "REFER_TO_CLERK"; // REFER_TO_CLERK, INCOMPLETE
    };
    String version = d.policyName() == null ? "n/a" : d.policyName() + "@v" + d.policyVersion();
    int days = d.validityDays() == null ? 90 : d.validityDays().intValue();
    return new PolicyDecision(outcome, reasons, version, days);
  }

  PolicyDecision evaluateFallback(PermitType type, Map<String, Object> facts, Throwable t) {
    log.warn("policy-service unavailable ({}), falling back to REFER_TO_CLERK", t.toString());
    return new PolicyDecision("REFER_TO_CLERK", List.of("POLICY_SERVICE_UNAVAILABLE"), "n/a", 90);
  }

  @Override
  @CircuitBreaker(name = "policy", fallbackMethod = "requiredFallback")
  public List<String> requiredFields(PermitType type) {
    RequiredFieldsResponse r = http.post().uri("/api/policies/required-fields")
        .body(new FactsRequest(Map.of("permit", Map.of("type", type.name())))).retrieve()
        .body(RequiredFieldsResponse.class);
    return r == null || r.requiredFields() == null || r.requiredFields().isEmpty()
        ? type.defaultRequiredFields() : r.requiredFields();
  }

  List<String> requiredFallback(PermitType type, Throwable t) {
    return type.defaultRequiredFields();
  }
}
