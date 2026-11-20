package il.ipp.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Wire format of every Kafka event. Kafka key = personId (keeps per-person ordering). */
public record EventEnvelope(
    String eventId, String type, int version, Instant occurredAt,
    String personId, String correlationId, Map<String, Object> payload) {

  public static EventEnvelope of(String type, String personId, String correlationId, Map<String, Object> payload) {
    return new EventEnvelope(UUID.randomUUID().toString(), type, 1, Instant.now(), personId, correlationId, payload);
  }

  public String str(String field) {
    Object v = payload == null ? null : payload.get(field);
    return v == null ? null : v.toString();
  }
}
