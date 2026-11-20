package il.ipp.audit.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AuditPorts {
  private AuditPorts() {}

  public interface AuditStore {
    /** Insert-only; duplicate id (redelivery) is ignored → idempotent. */
    void append(AuditRecord r);

    List<AuditRecord> search(String entityId, String personId, String actor, String action, Instant before, int limit);

    Map<String, Long> countByAction();
  }

  public record DeadLetter(String id, String topic, String originalTopic, int partition, long offset, String key,
                           String value, String error, Instant at, boolean replayed) {}

  public interface DeadLetterStore {
    void save(DeadLetter d);

    List<DeadLetter> recent(int limit);

    Optional<DeadLetter> find(String id);

    void markReplayed(String id);
  }
}
