package il.ipp.enforcement.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Enforcement domain: violation detection, case state machine, sanction policy. Framework-free. */
public final class Enforcement {
  private Enforcement() {}

  public enum Direction { ENTRY, EXIT }

  public enum ViolationType { OVERSTAY, STAY_WITHOUT_PERMIT, WORK_OUTSIDE_REGION }

  public enum SanctionType { ENTRY_BAN, PERMIT_SUSPENSION, WARNING }

  /**
   * Case lifecycle (closing the loop):
   * OPEN → SANCTIONED → APPEALED → UPHELD | OVERTURNED → CLOSED ; OPEN → DISMISSED.
   */
  public enum CaseStatus {
    OPEN, SANCTIONED, APPEALED, UPHELD, OVERTURNED, DISMISSED, CLOSED;

    private static final Map<CaseStatus, Set<CaseStatus>> NEXT = Map.of(
        OPEN, EnumSet.of(SANCTIONED, DISMISSED),
        SANCTIONED, EnumSet.of(APPEALED, CLOSED),
        APPEALED, EnumSet.of(UPHELD, OVERTURNED),
        UPHELD, EnumSet.of(CLOSED),
        OVERTURNED, EnumSet.of(CLOSED),
        DISMISSED, EnumSet.noneOf(CaseStatus.class),
        CLOSED, EnumSet.noneOf(CaseStatus.class));

    public CaseStatus to(CaseStatus target) {
      if (!NEXT.get(this).contains(target)) {
        throw new IllegalStateException("INVALID_TRANSITION: " + this + " → " + target);
      }
      return target;
    }
  }

  public record Crossing(UUID id, UUID personId, String nationalId, String gate, Direction direction,
                         Instant occurredAt, String source) {}

  public record PermitSnapshot(UUID permitId, String type, String status, LocalDate validFrom, LocalDate validTo,
                               String region) {
    public boolean validOn(LocalDate d) {
      return "ACTIVE".equals(status) && validFrom != null && validTo != null && !d.isBefore(validFrom)
          && !d.isAfter(validTo);
    }
  }

  public record Violation(ViolationType type, String details) {}

  public record ProposedSanction(SanctionType type, int days) {}

  /** Sanction policy table (in a full system this would be a policy-service rule). */
  public static ProposedSanction proposeFor(ViolationType t) {
    return switch (t) {
      case OVERSTAY -> new ProposedSanction(SanctionType.ENTRY_BAN, 90);
      case STAY_WITHOUT_PERMIT -> new ProposedSanction(SanctionType.ENTRY_BAN, 180);
      case WORK_OUTSIDE_REGION -> new ProposedSanction(SanctionType.PERMIT_SUSPENSION, 30);
    };
  }

  /** Detector 1 – on ENTRY: entering without any valid permit. */
  public static Optional<Violation> onEntry(Crossing c, List<PermitSnapshot> permits, LocalDate day) {
    if (c.direction() != Direction.ENTRY) {
      return Optional.empty();
    }
    boolean valid = permits.stream().anyMatch(p -> p.validOn(day));
    return valid ? Optional.empty()
        : Optional.of(new Violation(ViolationType.STAY_WITHOUT_PERMIT, "Entry at " + c.gate() + " without a valid permit"));
  }

  /** Detector 2 – periodic: person is inside (last crossing = ENTRY) and no permit is valid any more. */
  public static Optional<Violation> overstay(boolean inside, Instant lastEntry, List<PermitSnapshot> permits,
                                             LocalDate day) {
    if (!inside || permits.stream().anyMatch(p -> p.validOn(day))) {
      return Optional.empty();
    }
    LocalDate lastValid = permits.stream().map(PermitSnapshot::validTo).filter(d -> d != null)
        .max(LocalDate::compareTo).orElse(null);
    if (lastValid == null) {
      return Optional.empty(); // handled by detector 1 at entry time
    }
    return Optional.of(new Violation(ViolationType.OVERSTAY,
        "Entered " + lastEntry + ", no exit recorded; permit expired " + lastValid));
  }

  /** Detector 3 – work-site check reported outside the permitted work region. */
  public static Optional<Violation> workRegion(String observedRegion, List<PermitSnapshot> permits, LocalDate day) {
    return permits.stream().filter(p -> p.validOn(day) && "WORK_ENTRY".equals(p.type())).findFirst()
        .filter(p -> p.region() != null && !p.region().equals(observedRegion))
        .map(p -> new Violation(ViolationType.WORK_OUTSIDE_REGION,
            "Observed in " + observedRegion + ", permitted region " + p.region()));
  }
}
