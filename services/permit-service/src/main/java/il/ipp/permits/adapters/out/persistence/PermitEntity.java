package il.ipp.permits.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import il.ipp.permits.domain.PermitStatus;
import il.ipp.permits.domain.PermitType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity (adapter side). The domain Permit stays framework-free; mapping in JpaPermitRepository. */
@Entity
@Table(name = "permit")
class PermitEntity {
  @Id
  UUID id;
  @Column(name = "person_id", nullable = false)
  UUID personId;
  @Column(name = "national_id")
  String nationalId;
  @Enumerated(EnumType.STRING)
  PermitType type;
  @Enumerated(EnumType.STRING)
  PermitStatus status;
  @Column(name = "employer_id")
  UUID employerId;
  @Column(name = "license_id")
  UUID licenseId;
  String region;
  @Column(name = "valid_from")
  LocalDate validFrom;
  @Column(name = "valid_to")
  LocalDate validTo;
  @Column(name = "policy_version")
  String policyVersion;
  String recommendation;
  @JdbcTypeCode(SqlTypes.JSON)
  List<String> reasons;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "form_data")
  Map<String, Object> formData;
  @Column(name = "submitted_at")
  Instant submittedAt;
  @Column(name = "decided_at")
  Instant decidedAt;
  @Column(name = "decided_by")
  String decidedBy;
  /** Optimistic locking: two clerks deciding the same application → the second gets 409. */
  @Version
  long version;
}
