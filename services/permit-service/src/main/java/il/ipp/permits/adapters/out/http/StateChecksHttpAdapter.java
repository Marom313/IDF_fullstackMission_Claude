package il.ipp.permits.adapters.out.http;

import il.ipp.permits.domain.PermitPorts.StateChecks;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter to state systems (mock-state-api in dev; real adapter in production – Ports &amp; Adapters). */
@Component
class StateChecksHttpAdapter implements StateChecks {
  private static final Logger log = LoggerFactory.getLogger(StateChecksHttpAdapter.class);
  private final RestClient http;

  StateChecksHttpAdapter(@Value("${ipp.mock-state.url:http://mock-state-api:8080}") String baseUrl) {
    var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    rf.setConnectTimeout(Duration.ofSeconds(1));
    rf.setReadTimeout(Duration.ofMillis(1500));
    this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
  }

  record SecurityResponse(String status) {}

  @Override
  @CircuitBreaker(name = "mockState", fallbackMethod = "securityFallback")
  @Retry(name = "mockState")
  public String securityStatus(String nationalId) {
    SecurityResponse r = http.get().uri("/api/state/security/{id}", nationalId).retrieve()
        .body(SecurityResponse.class);
    return r == null || r.status() == null ? "UNKNOWN" : r.status();
  }

  String securityFallback(String nationalId, Throwable t) {
    log.warn("mock-state-api unavailable: {}", t.toString());
    return "UNAVAILABLE";
  }
}
