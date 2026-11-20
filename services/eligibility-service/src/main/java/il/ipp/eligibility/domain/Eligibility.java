package il.ipp.eligibility.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pure domain of the eligibility read model (CQRS-lite query side). No framework imports. */
public final class Eligibility {
  private Eligibility() {}

  public enum Purpose { ENTRY, EXIT, WORK }

  public enum Decision { ALLOW, DENY, REFER_TO_CLERK }

  public record PermitView(UUID permitId, String type, String status, LocalDate validFrom, LocalDate validTo,
                           String region, String policyVersion) {
    boolean validOn(LocalDate day) {
      return "ACTIVE".equals(status) && validFrom != null && validTo != null
          && !day.isBefore(validFrom) && !day.isAfter(validTo);
    }
  }

  public record SanctionView(UUID sanctionId, String type, LocalDate until, String reason) {
    boolean activeOn(LocalDate day) {
      return until == null || !day.isAfter(until);
    }
  }

  /** Everything needed to decide, cached in Redis as one document per person. */
  public record View(UUID personId, String nationalId, String fullName, String securityStatus, String region,
                     List<PermitView> permits, List<SanctionView> sanctions) {}

  public record Result(Decision decision, List<String> reasons, String policyVersion, UUID permitId,
                       Instant checkedAt) {}

  /**
   * The decision rules (ordered). Reasons are machine codes – the UI translates them in 4 languages.
   */
  public static Result decide(View v, Purpose purpose, String atRegion, LocalDate today, Instant now) {
    List<String> reasons = new ArrayList<>();
    if (purpose == Purpose.EXIT) {
      reasons.add("EXIT_ALWAYS_ALLOWED");
      return new Result(Decision.ALLOW, reasons, "core-rules-v1", null, now);
    }
    Optional<SanctionView> sanction = v.sanctions().stream().filter(s -> s.activeOn(today)).findFirst();
    if (sanction.isPresent()) {
      SanctionView s = sanction.get();
      reasons.add("ACTIVE_SANCTION:" + s.type() + (s.until() != null ? ":" + s.until() : ""));
      return new Result(Decision.DENY, reasons, "core-rules-v1", null, now);
    }
    if ("DENIED".equals(v.securityStatus())) {
      reasons.add("SECURITY_DENIED");
      return new Result(Decision.DENY, reasons, "core-rules-v1", null, now);
    }
    Optional<PermitView> permit = v.permits().stream()
        .filter(p -> p.validOn(today))
        .filter(p -> purpose != Purpose.WORK || "WORK_ENTRY".equals(p.type()))
        .findFirst();
    if (permit.isEmpty()) {
      if (v.permits().stream().anyMatch(p -> "SUSPENDED".equals(p.status()))) {
        reasons.add("PERMIT_SUSPENDED");
      } else {
        v.permits().stream().filter(p -> p.validTo() != null && p.validTo().isBefore(today))
            .map(PermitView::validTo).max(LocalDate::compareTo)
            .ifPresentOrElse(d -> reasons.add("PERMIT_EXPIRED:" + d), () -> reasons.add("NO_VALID_PERMIT"));
      }
      return new Result(Decision.DENY, reasons, "core-rules-v1", null, now);
    }
    PermitView p = permit.get();
    if (!"CLEAR".equals(v.securityStatus())) {
      reasons.add("SECURITY_" + v.securityStatus());
      return new Result(Decision.REFER_TO_CLERK, reasons, p.policyVersion(), p.permitId(), now);
    }
    if (purpose == Purpose.WORK && atRegion != null && p.region() != null && !atRegion.equals(p.region())) {
      reasons.add("OUTSIDE_PERMITTED_REGION:" + p.region());
      return new Result(Decision.DENY, reasons, p.policyVersion(), p.permitId(), now);
    }
    reasons.add("VALID_PERMIT:" + p.type() + ":" + p.validTo());
    return new Result(Decision.ALLOW, reasons, p.policyVersion(), p.permitId(), now);
  }
}
