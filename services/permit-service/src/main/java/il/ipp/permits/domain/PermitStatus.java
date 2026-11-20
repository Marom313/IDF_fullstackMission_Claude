package il.ipp.permits.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Permit state machine:
 * DRAFT → SUBMITTED → IN_REVIEW → APPROVED | REJECTED ; APPROVED → ACTIVE → EXPIRED | SUSPENDED | REVOKED
 * SUSPENDED → ACTIVE (sanction lifted) | REVOKED.
 */
public enum PermitStatus {
  DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, ACTIVE, EXPIRED, SUSPENDED, REVOKED;

  private static final Map<PermitStatus, Set<PermitStatus>> NEXT = Map.of(
      DRAFT, EnumSet.of(SUBMITTED),
      SUBMITTED, EnumSet.of(IN_REVIEW, APPROVED, REJECTED),
      IN_REVIEW, EnumSet.of(APPROVED, REJECTED),
      APPROVED, EnumSet.of(ACTIVE, REVOKED),
      ACTIVE, EnumSet.of(EXPIRED, SUSPENDED, REVOKED),
      SUSPENDED, EnumSet.of(ACTIVE, REVOKED, EXPIRED),
      REJECTED, EnumSet.noneOf(PermitStatus.class),
      EXPIRED, EnumSet.noneOf(PermitStatus.class),
      REVOKED, EnumSet.noneOf(PermitStatus.class));

  public boolean canMoveTo(PermitStatus target) {
    return NEXT.get(this).contains(target);
  }

  public boolean isTerminal() {
    return NEXT.get(this).isEmpty();
  }
}
