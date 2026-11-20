package il.ipp.enforcement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import il.ipp.enforcement.domain.Enforcement.CaseStatus;
import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.Direction;
import il.ipp.enforcement.domain.Enforcement.PermitSnapshot;
import il.ipp.enforcement.domain.Enforcement.ViolationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnforcementTest {
  static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

  static PermitSnapshot permit(LocalDate from, LocalDate to) {
    return new PermitSnapshot(UUID.randomUUID(), "WORK_ENTRY", "ACTIVE", from, to, "CENTER");
  }

  static Crossing entry() {
    return new Crossing(UUID.randomUUID(), UUID.randomUUID(), "900000001", "GATE-CENTER-1", Direction.ENTRY,
        Instant.now(), "TEST");
  }

  @Test
  void entryWithoutPermitIsViolation() {
    var v = Enforcement.onEntry(entry(), List.of(), TODAY);
    assertThat(v).isPresent();
    assertThat(v.get().type()).isEqualTo(ViolationType.STAY_WITHOUT_PERMIT);
  }

  @Test
  void entryWithValidPermitIsFine() {
    assertThat(Enforcement.onEntry(entry(), List.of(permit(TODAY.minusDays(1), TODAY.plusDays(5))), TODAY)).isEmpty();
  }

  @Test
  void insideAfterPermitExpiryIsOverstay() {
    var v = Enforcement.overstay(true, Instant.now(), List.of(permit(TODAY.minusDays(40), TODAY.minusDays(2))), TODAY);
    assertThat(v).map(x -> x.type()).contains(ViolationType.OVERSTAY);
  }

  @Test
  void outsideIsNeverOverstay() {
    assertThat(Enforcement.overstay(false, Instant.now(), List.of(permit(TODAY.minusDays(40), TODAY.minusDays(2))),
        TODAY)).isEmpty();
  }

  @Test
  void sanctionPolicy() {
    assertThat(Enforcement.proposeFor(ViolationType.OVERSTAY).days()).isEqualTo(90);
  }

  @Test
  void caseStateMachine() {
    assertThat(CaseStatus.OPEN.to(CaseStatus.SANCTIONED)).isEqualTo(CaseStatus.SANCTIONED);
    assertThatThrownBy(() -> CaseStatus.CLOSED.to(CaseStatus.OPEN)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> CaseStatus.OPEN.to(CaseStatus.APPEALED)).isInstanceOf(IllegalStateException.class);
  }
}
