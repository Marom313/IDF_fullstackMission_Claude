package il.ipp.audit.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable audit entry: who, when, what, which policy version, from where (correlation id).
 * Append-only: there is no update or delete anywhere in this service, and the Mongo user can only insert/find.
 */
public record AuditRecord(String id, Instant at, String service, String action, String entityType, String entityId,
                          String personId, String actor, String decision, List<String> reasons, String policyVersion,
                          String correlationId) {
  public AuditRecord {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(at, "at");
    Objects.requireNonNull(action, "action");
  }
}
