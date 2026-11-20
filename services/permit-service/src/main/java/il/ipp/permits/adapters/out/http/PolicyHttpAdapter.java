package il.ipp.permits.adapters.out.http;

import il.ipp.permits.domain.PermitPorts.PolicyDecision;
import il.ipp.permits.domain.PermitPorts.PolicyEvaluator;
import il.ipp.permits.domain.PermitType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter to policy-service (DSL evaluator). Circuit breaker + retry with jitter + short timeouts.
 * Fallback: REFER_TO_CLERK with an explicit reason (never auto-approve when policy is unknown).
 */
@Component
class PolicyHttpAdapter implements PolicyEvaluator {
  private static final Logger log = LoggerFactory.getLogger(PolicyHttpAdapter.class);
  private final RestClient http;

  PolicyHttpAdapter(@Value("${ipp.policy.url:http://policy-service:8080}") String baseUrl) {
    var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    rf.setConnectTimeout(Duration.ofSeconds(1));
    rf.setReadTimeout(Duration.ofSeconds(2));
    this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
  }

  record EvaluateRequest(String permitType, Map<String, Object> facts) {}

  record EvaluateResponse(String decision, List<String> reasons, String policyVersion, Integer validityDays) {}

  record RequiredFieldsResponse(List<String> requiredFields) {}

  @Override
  @CircuitBreaker(name = "policy", fallbackMethod = "evaluateFallback")
  @Retry(name = "policy")
  public PolicyDecision evaluate(PermitType type, Map<String, Object> facts) {
    EvaluateResponse r = http.post().uri("/internal/policies/evaluate")
        .body(new EvaluateRequest(type.name(), facts)).retrieve().body(EvaluateResponse.class);
    if (r == null) {
      throw new IllegalStateException("empty policy response");
    }
    return new PolicyDecision(r.decision(), r.reasons() == null ? List.of() : r.reasons(), r.policyVersion(),
        r.validityDays() == null ? 90 : r.validityDays());
  }

  PolicyDecision evaluateFallback(PermitType type, Map<String, Object> facts, Throwable t) {
    log.warn("policy-service unavailable ({}), falling back to REFER_TO_CLERK", t.toString());
    return new PolicyDecision("REFER_TO_CLERK", List.of("POLICY_SERVICE_UNAVAILABLE"), "n/a", 90);
  }

  @Override
  @CircuitBreaker(name = "policy", fallbackMethod = "requiredFallback")
  public List<String> requiredFields(PermitType type) {
    RequiredFieldsResponse r = http.get().uri("/internal/policies/required-fields?permitType={t}", type.name())
        .retrieve().body(new ParameterizedTypeReference<>() {});
    return r == null || r.requiredFields() == null ? type.defaultRequiredFields() : r.requiredFields();
  }

  List<String> requiredFallback(PermitType type, Throwable t) {
    return type.defaultRequiredFields();
  }
}
