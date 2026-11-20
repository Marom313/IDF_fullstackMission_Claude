package il.ipp.eligibility.domain;

import il.ipp.eligibility.domain.Eligibility.View;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class EligibilityPorts {
  private EligibilityPorts() {}

  /** Postgres read model (rebuilt from events; never the source of truth). */
  public interface ReadModel {
    Optional<View> load(UUID personId);

    Optional<UUID> personIdOf(String nationalId);

    void upsertPerson(UUID personId, String nationalId, String fullName, String securityStatus, String region);

    void upsertPermit(UUID permitId, UUID personId, String type, String status, LocalDate validFrom,
                      LocalDate validTo, String region, String policyVersion);

    void upsertSanction(UUID sanctionId, UUID personId, String type, LocalDate until, String reason, boolean active);
  }

  /** Redis cache of views. Every key has a TTL. */
  public interface ViewCache {
    Optional<View> get(UUID personId);

    void put(UUID personId, View view, Duration ttl);

    void evict(UUID personId);

    Optional<UUID> personIdOf(String nationalId);

    void putPersonId(String nationalId, UUID personId);

    /** Short lock for single-flight rebuild (stampede protection). */
    boolean tryLock(UUID personId, Duration ttl);

    void unlock(UUID personId);
  }

  /** Fire-and-forget audit of every check. */
  public interface AuditSink {
    void checked(View v, String purpose, Eligibility.Result r, String actor);
  }
}
