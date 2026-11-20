package il.ipp.enforcement.domain;

import il.ipp.enforcement.domain.Enforcement.CaseStatus;
import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.PermitSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class EnforcementPorts {
  private EnforcementPorts() {}

  public record CaseRecord(UUID id, UUID personId, String nationalId, String violationType, String details,
                           String status, String proposedSanction, int proposedDays, UUID sanctionId,
                           LocalDate sanctionUntil, String appealText, String decisionNote, Instant openedAt,
                           Instant updatedAt, String updatedBy, long version) {}

  public interface CaseRepository {
    Optional<CaseRecord> find(UUID id);

    boolean hasOpenCase(UUID personId, String violationType);

    void insert(CaseRecord c);

    /** Optimistic: update only if version matches; returns false on conflict. */
    boolean update(CaseRecord c);

    List<CaseRecord> list(String status, String after, int limit);

    List<CaseRecord> byPerson(UUID personId);

    Map<String, Long> countByStatus();
  }

  public interface CrossingRepository {
    void insert(Crossing c);

    void insertAll(List<Crossing> list);

    List<Crossing> byPerson(UUID personId, int limit);

    long count();

    /** Presence read model: last crossing per person. */
    void updatePresence(UUID personId, String nationalId, boolean inside, Instant at, String gate);

    List<UUID> insideSince(Instant before, int limit);

    Optional<Instant> lastEntry(UUID personId);
  }

  public interface PermitSnapshots {
    void upsert(UUID personId, PermitSnapshot p);

    List<PermitSnapshot> of(UUID personId);
  }

  public record Stats(Map<String, Long> casesByStatus, long crossings) {}

  public static Instant now() {
    return Instant.now();
  }

  public static final CaseStatus INITIAL = CaseStatus.OPEN;
}
