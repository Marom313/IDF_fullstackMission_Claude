package il.ipp.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.web.CorrelationId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: the event row is written in the SAME transaction as the business change.
 * Propagation.MANDATORY makes calling it outside a transaction fail fast (prevents the dual-write bug).
 */
public class OutboxWriter {
  private static final String INSERT =
      "insert into outbox(id, topic, msg_key, payload, created_at) values (?::uuid, ?, ?, ?::jsonb, ?)";
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public OutboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public EventEnvelope write(String topic, String type, String personId, Map<String, Object> payload) {
    EventEnvelope env = EventEnvelope.of(type, personId, CorrelationId.current(), payload);
    jdbc.update(INSERT, env.eventId(), topic, personId, toJson(env), Timestamp.from(env.occurredAt()));
    return env;
  }

  /** Batch variant (seeding). Entry key = personId, value = payload. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void writeAll(String topic, String type, List<Map.Entry<String, Map<String, Object>>> items) {
    List<Object[]> rows = new ArrayList<>(items.size());
    for (var it : items) {
      EventEnvelope env = EventEnvelope.of(type, it.getKey(), "seed", it.getValue());
      rows.add(new Object[] {env.eventId(), topic, it.getKey(), toJson(env), Timestamp.from(env.occurredAt())});
    }
    jdbc.batchUpdate(INSERT, rows);
  }

  private String toJson(EventEnvelope env) {
    try {
      return json.writeValueAsString(env);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
