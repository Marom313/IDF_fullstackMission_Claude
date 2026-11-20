package il.ipp.eligibility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import il.ipp.eligibility.domain.Eligibility.Decision;
import il.ipp.eligibility.domain.Eligibility.PermitView;
import il.ipp.eligibility.domain.Eligibility.Purpose;
import il.ipp.eligibility.domain.Eligibility.SanctionView;
import il.ipp.eligibility.domain.Eligibility.View;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibilityTest {
  static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

  static View view(String security, List<PermitView> permits, List<SanctionView> sanctions) {
    return new View(UUID.randomUUID(), "900000001", "Demo", security, "CENTER", permits, sanctions);
  }

  static PermitView work(String status, LocalDate from, LocalDate to) {
    return new PermitView(UUID.randomUUID(), "WORK_ENTRY", status, from, to, "CENTER", "work-v3");
  }

  @Test
  void validPermitAllows() {
    var r = Eligibility.decide(view("CLEAR", List.of(work("ACTIVE", TODAY.minusDays(5), TODAY.plusDays(30))),
        List.of()), Purpose.ENTRY, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.ALLOW);
    assertThat(r.policyVersion()).isEqualTo("work-v3");
  }

  @Test
  void activeSanctionDeniesEvenWithPermit() {
    var r = Eligibility.decide(view("CLEAR", List.of(work("ACTIVE", TODAY.minusDays(5), TODAY.plusDays(30))),
            List.of(new SanctionView(UUID.randomUUID(), "ENTRY_BAN", TODAY.plusDays(90), "overstay"))),
        Purpose.ENTRY, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.DENY);
    assertThat(r.reasons().get(0)).startsWith("ACTIVE_SANCTION:ENTRY_BAN");
  }

  @Test
  void expiredPermitDeniesWithDate() {
    var r = Eligibility.decide(view("CLEAR", List.of(work("EXPIRED", TODAY.minusDays(100), TODAY.minusDays(10))),
        List.of()), Purpose.ENTRY, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.DENY);
    assertThat(r.reasons()).containsExactly("PERMIT_EXPIRED:" + TODAY.minusDays(10));
  }

  @Test
  void pendingSecurityRefersToClerk() {
    var r = Eligibility.decide(view("PENDING", List.of(work("ACTIVE", TODAY, TODAY.plusDays(1))), List.of()),
        Purpose.ENTRY, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.REFER_TO_CLERK);
  }

  @Test
  void workOutsideRegionDenied() {
    var r = Eligibility.decide(view("CLEAR", List.of(work("ACTIVE", TODAY, TODAY.plusDays(1))), List.of()),
        Purpose.WORK, "HAIFA", TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.DENY);
    assertThat(r.reasons().get(0)).startsWith("OUTSIDE_PERMITTED_REGION");
  }

  @Test
  void permitValidUntilEndOfLastDay() {
    var r = Eligibility.decide(view("CLEAR", List.of(work("ACTIVE", TODAY.minusDays(1), TODAY)), List.of()),
        Purpose.ENTRY, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.ALLOW);
  }

  @Test
  void exitAlwaysAllowed() {
    var r = Eligibility.decide(view("DENIED", List.of(), List.of()), Purpose.EXIT, null, TODAY, Instant.now());
    assertThat(r.decision()).isEqualTo(Decision.ALLOW);
  }
}
