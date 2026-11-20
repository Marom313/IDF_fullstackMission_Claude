package il.ipp.enforcement.adapters.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.events.Topics;
import il.ipp.common.inbox.InboxGuard;
import il.ipp.enforcement.application.EnforcementService;
import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.Direction;
import il.ipp.enforcement.domain.Enforcement.PermitSnapshot;
import il.ipp.enforcement.domain.EnforcementPorts.PermitSnapshots;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EnforcementListeners {
  private final ObjectMapper json;
  private final InboxGuard inbox;
  private final EnforcementService service;
  private final PermitSnapshots permits;

  EnforcementListeners(ObjectMapper json, InboxGuard inbox, EnforcementService service, PermitSnapshots permits) {
    this.json = json;
    this.inbox = inbox;
    this.service = service;
    this.permits = permits;
  }

  /**
   * Crossing events from NiFi (whitelisted ingest) or the officer terminal. NiFi publishes the raw
   * normalized record (not an envelope); both shapes are accepted. Bad records → exception → retries → DLT.
   */
  @KafkaListener(topics = Topics.CROSSING_RECORDED, groupId = "enforcement-service.crossings")
  @Transactional
  public void onCrossing(String message) throws Exception {
    var node = json.readTree(message);
    var p = node.has("payload") ? node.get("payload") : node;
    String id = p.path("crossingId").asText(node.path("eventId").asText(null));
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("crossing without id");
    }
    if (!inbox.firstTime(id, "enforcement.crossing")) {
      return;
    }
    Crossing c = new Crossing(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        UUID.fromString(p.get("personId").asText()), p.path("nationalId").asText(null), p.get("gate").asText(),
        Direction.valueOf(p.get("direction").asText()), Instant.parse(p.get("occurredAt").asText()),
        p.path("source").asText("NIFI"));
    service.recordCrossing(c);
  }

  @KafkaListener(topics = Topics.PERMIT_STATUS_CHANGED, groupId = "enforcement-service.permits")
  @Transactional
  public void onPermit(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "enforcement.permit")) {
      return;
    }
    permits.upsert(UUID.fromString(e.str("personId")), new PermitSnapshot(UUID.fromString(e.str("permitId")),
        e.str("type"), e.str("status"), date(e.str("validFrom")), date(e.str("validTo")), e.str("region")));
  }

  private static LocalDate date(String s) {
    return s == null || s.isBlank() || "null".equals(s) ? null : LocalDate.parse(s);
  }
}
