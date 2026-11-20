package il.ipp.eligibility.adapters.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.events.Topics;
import il.ipp.common.web.CorrelationId;
import il.ipp.eligibility.domain.Eligibility.Result;
import il.ipp.eligibility.domain.Eligibility.View;
import il.ipp.eligibility.domain.EligibilityPorts.AuditSink;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Every eligibility decision is audited. The hot path has no DB write, so this publishes directly
 * (async, batched by linger.ms) instead of going through an outbox. Trade-off documented in GAPS.
 */
@Component
class KafkaAuditSink implements AuditSink {
  private static final Logger log = LoggerFactory.getLogger(KafkaAuditSink.class);
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;

  KafkaAuditSink(KafkaTemplate<String, String> kafka, ObjectMapper json) {
    this.kafka = kafka;
    this.json = json;
  }

  @Override
  public void checked(View v, String purpose, Result r, String actor) {
    try {
      Map<String, Object> p = new HashMap<>();
      p.put("service", "eligibility-service");
      p.put("action", "ELIGIBILITY_CHECK_" + purpose);
      p.put("entityType", "PERSON");
      p.put("entityId", v.personId().toString());
      p.put("actor", actor);
      p.put("decision", r.decision().name());
      p.put("reasons", r.reasons());
      p.put("policyVersion", r.policyVersion());
      EventEnvelope env = EventEnvelope.of("AuditRecord", v.personId().toString(), CorrelationId.current(), p);
      kafka.send(Topics.AUDIT_DECISION, env.personId(), json.writeValueAsString(env))
          .whenComplete((ok, ex) -> {
            if (ex != null) {
              log.warn("audit publish failed: {}", ex.toString());
            }
          });
    } catch (Exception e) {
      log.warn("audit publish failed: {}", e.toString());
    }
  }
}
