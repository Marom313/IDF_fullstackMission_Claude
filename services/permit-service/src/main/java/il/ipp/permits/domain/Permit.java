package il.ipp.permits.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Permit aggregate: an application that becomes a permit. All transitions go through the state machine. */
public class Permit {
  private final UUID id;
  private final UUID personId;
  private final String nationalId;
  private final PermitType type;
  private PermitStatus status;
  private final UUID employerId;
  private final UUID licenseId;
  private final String region;
  private LocalDate validFrom;
  private LocalDate validTo;
  private String policyVersion;
  private String recommendation;
  private List<String> reasons;
  private final Map<String, Object> formData;
  private final Instant submittedAt;
  private Instant decidedAt;
  private String decidedBy;
  private final long version;

  public Permit(UUID id, UUID personId, String nationalId, PermitType type, PermitStatus status, UUID employerId,
                UUID licenseId, String region, LocalDate validFrom, LocalDate validTo, String policyVersion,
                String recommendation, List<String> reasons, Map<String, Object> formData, Instant submittedAt,
                Instant decidedAt, String decidedBy, long version) {
    this.id = Objects.requireNonNull(id);
    this.personId = Objects.requireNonNull(personId);
    this.nationalId = nationalId;
    this.type = Objects.requireNonNull(type);
    this.status = Objects.requireNonNull(status);
    this.employerId = employerId;
    this.licenseId = licenseId;
    this.region = region;
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.policyVersion = policyVersion;
    this.recommendation = recommendation;
    this.reasons = reasons == null ? new ArrayList<>() : new ArrayList<>(reasons);
    this.formData = formData == null ? new HashMap<>() : new HashMap<>(formData);
    this.submittedAt = submittedAt;
    this.decidedAt = decidedAt;
    this.decidedBy = decidedBy;
    this.version = version;
  }

  public static Permit submit(UUID personId, String nationalId, PermitType type, UUID employerId, UUID licenseId,
                              String region, Map<String, Object> formData, List<String> requiredFields) {
    List<String> missing = requiredFields.stream()
        .filter(f -> !f.equals("photo") && !f.equals("idNumber"))
        .filter(f -> formData == null || formData.get(f) == null || formData.get(f).toString().isBlank())
        .toList();
    if (!missing.isEmpty()) {
      throw new PermitRuleViolation("MISSING_REQUIRED_FIELDS", "Missing required fields: " + missing);
    }
    return new Permit(UUID.randomUUID(), personId, nationalId, type, PermitStatus.SUBMITTED, employerId, licenseId,
        region, null, null, null, null, List.of(), formData, Instant.now(), null, null, 0);
  }

  public void recordRecommendation(String decision, List<String> why, String policyVer) {
    this.recommendation = decision;
    this.reasons = new ArrayList<>(why);
    this.policyVersion = policyVer;
  }

  public void startReview() {
    moveTo(PermitStatus.IN_REVIEW);
  }

  /** Approve and activate immediately (validity starts today). */
  public void approve(String clerk, int validityDays, LocalDate today, List<String> why) {
    if (validityDays <= 0 || validityDays > 365) {
      throw new PermitRuleViolation("INVALID_VALIDITY", "Validity must be 1..365 days");
    }
    moveTo(PermitStatus.APPROVED);
    this.validFrom = today;
    this.validTo = today.plusDays(validityDays);
    this.decidedBy = clerk;
    this.decidedAt = Instant.now();
    if (why != null && !why.isEmpty()) {
      this.reasons = new ArrayList<>(why);
    }
    moveTo(PermitStatus.ACTIVE);
  }

  public void reject(String clerk, List<String> why) {
    if (why == null || why.isEmpty()) {
      throw new PermitRuleViolation("REASON_REQUIRED", "Rejection requires a reason");
    }
    moveTo(PermitStatus.REJECTED);
    this.reasons = new ArrayList<>(why);
    this.decidedBy = clerk;
    this.decidedAt = Instant.now();
  }

  public void expire() {
    moveTo(PermitStatus.EXPIRED);
  }

  public void suspend() {
    moveTo(PermitStatus.SUSPENDED);
  }

  public void reinstate() {
    moveTo(PermitStatus.ACTIVE);
  }

  public void revoke() {
    moveTo(PermitStatus.REVOKED);
  }

  public boolean isValidOn(LocalDate day) {
    return status == PermitStatus.ACTIVE && validFrom != null && !day.isBefore(validFrom) && !day.isAfter(validTo);
  }

  private void moveTo(PermitStatus target) {
    if (!status.canMoveTo(target)) {
      throw new PermitRuleViolation("INVALID_TRANSITION", "Cannot move permit from " + status + " to " + target);
    }
    this.status = target;
  }

  public UUID id() { return id; }
  public UUID personId() { return personId; }
  public String nationalId() { return nationalId; }
  public PermitType type() { return type; }
  public PermitStatus status() { return status; }
  public UUID employerId() { return employerId; }
  public UUID licenseId() { return licenseId; }
  public String region() { return region; }
  public LocalDate validFrom() { return validFrom; }
  public LocalDate validTo() { return validTo; }
  public String policyVersion() { return policyVersion; }
  public String recommendation() { return recommendation; }
  public List<String> reasons() { return List.copyOf(reasons); }
  public Map<String, Object> formData() { return Map.copyOf(formData); }
  public Instant submittedAt() { return submittedAt; }
  public Instant decidedAt() { return decidedAt; }
  public String decidedBy() { return decidedBy; }
  public long version() { return version; }
}
