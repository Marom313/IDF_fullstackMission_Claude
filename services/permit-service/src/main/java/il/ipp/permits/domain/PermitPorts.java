package il.ipp.permits.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Outbound ports (Hexagonal). */
public final class PermitPorts {
  private PermitPorts() {}

  public interface PermitRepository {
    Optional<Permit> findById(UUID id);

    Permit save(Permit p);

    List<Permit> byPerson(UUID personId);

    /** Keyset by (submitted_at, id). */
    List<Permit> queue(List<PermitStatus> statuses, String afterCursor, int limit);

    List<Permit> byEmployer(UUID employerId, PermitStatus status, String afterCursor, int limit);

    List<UUID> activeExpiredBefore(LocalDate day, int limit);

    long count();

    Map<String, Long> countByStatus();

    void insertAll(List<Permit> permits);
  }

  /** Read model of persons (fed by registry.person.upserted.v1). */
  public record PersonRef(UUID personId, String nationalId, String fullName, LocalDate birthDate, String securityStatus,
                   String region, UUID employerId) {}

  public interface PersonRefRepository {
    Optional<PersonRef> find(UUID personId);

    Optional<PersonRef> findByNationalId(String nationalId);

    void upsert(PersonRef ref);
  }

  public record License(UUID id, UUID employerId, String sector, int workersQuota, int workersAssigned, LocalDate validTo,
                 String status) {}

  public record SectorQuota(String sector, int total, int used) {
    public int remaining() {
      return total - used;
    }
  }

  public interface LicenseRepository {
    Optional<License> findById(UUID id);

    Optional<License> byEmployer(UUID employerId);

    List<SectorQuota> quotas();

    /** Atomically consumes one slot of the sector quota and the license quota; false if exhausted. */
    boolean consumeQuota(UUID licenseId, String sector);
  }

  /** Port to policy-service (DSL). */
  public record PolicyDecision(String decision, List<String> reasons, String policyVersion, int validityDays) {}

  public interface PolicyEvaluator {
    PolicyDecision evaluate(PermitType type, Map<String, Object> facts);

    List<String> requiredFields(PermitType type);
  }

  /** Port to state systems (mock-state-api in dev). */
  public interface StateChecks {
    String securityStatus(String nationalId);
  }

  /** Signs permit QR payloads (Ed25519). */
  public interface QrSigner {
    String sign(String payloadJson);

    /** @return payload JSON if valid, else null */
    String verify(String qr);

    String publicKeySpkiBase64();
  }

  /** Renders the SPECIMEN permit PDF. */
  public interface DocumentRenderer {
    byte[] permitPdf(Permit permit, PersonRef person, String qr);
  }
}
