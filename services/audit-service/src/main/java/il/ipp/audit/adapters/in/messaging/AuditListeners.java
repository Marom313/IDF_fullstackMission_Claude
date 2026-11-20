package il.ipp.audit.adapters.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.audit.domain.AuditPorts.AuditStore;
import il.ipp.audit.domain.AuditPorts.DeadLetter;
import il.ipp.audit.domain.AuditPorts.DeadLetterStore;
import il.ipp.audit.domain.AuditRecord;
import il.ipp.common.events.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AuditListeners {
  private final ObjectMapper json;
  private final AuditStore audit;
  private final DeadLetterStore dlt;

  AuditListeners(ObjectMapper json, AuditStore audit, DeadLetterStore dlt) {
    this.json = json;
    this.audit = audit;
    this.dlt = dlt;
  }

  /** eventId is used as Mongo _id → redelivery is idempotent without an inbox table. */
  @KafkaListener(topics = {Topics.AUDIT_DECISION, Topics.AUDIT_INGEST}, groupId = "audit-service")
  public void onAudit(String message) throws Exception {
    JsonNode env = json.readTree(message);
    JsonNode p = env.path("payload");
    List<String> reasons = new ArrayList<>();
    p.path("reasons").forEach(n -> reasons.add(n.asText()));
    audit.append(new AuditRecord(env.get("eventId").asText(), Instant.parse(env.get("occurredAt").asText()),
        p.path("service").asText(null), p.path("action").asText("UNKNOWN"), p.path("entityType").asText(null),
        p.path("entityId").asText(null), env.path("personId").asText(null), p.path("actor").asText(null),
        p.path("decision").asText(null), reasons, p.path("policyVersion").asText(null),
        env.path("correlationId").asText(null)));
  }

  /** Captures every dead-letter topic for the admin DLT screen (replay). */
  @KafkaListener(topicPattern = ".*\\.DLT", groupId = "audit-service.dlt",
      properties = {"metadata.max.age.ms=10000"})
  public void onDeadLetter(ConsumerRecord<String, String> rec) {
    String original = header(rec, "kafka_dlt-original-topic");
    String error = header(rec, "kafka_dlt-exception-message");
    dlt.save(new DeadLetter(rec.topic() + "-" + rec.partition() + "-" + rec.offset(), rec.topic(),
        original != null ? original : rec.topic().replace(".DLT", ""), rec.partition(), rec.offset(), rec.key(),
        rec.value(), error, Instant.ofEpochMilli(rec.timestamp()), false));
  }

  private static String header(ConsumerRecord<String, String> rec, String name) {
    Header h = rec.headers().lastHeader(name);
    return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
  }
}
