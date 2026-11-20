package il.ipp.permits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermitTest {

  private Permit submitted() {
    return Permit.submit(UUID.randomUUID(), "900000001", PermitType.WORK_ENTRY, UUID.randomUUID(), UUID.randomUUID(),
        "CENTER", Map.of("magneticCard", "MC-1", "employerId", "e", "workRegion", "CENTER"),
        PermitType.WORK_ENTRY.defaultRequiredFields());
  }

  @Test
  void approveActivatesWithValidity() {
    Permit p = submitted();
    p.approve("clerk1", 90, LocalDate.of(2026, 11, 20), List.of());
    assertThat(p.status()).isEqualTo(PermitStatus.ACTIVE);
    assertThat(p.validTo()).isEqualTo(LocalDate.of(2027, 2, 18));
    assertThat(p.isValidOn(LocalDate.of(2026, 12, 1))).isTrue();
    assertThat(p.isValidOn(LocalDate.of(2027, 3, 1))).isFalse();
  }

  @Test
  void cannotApproveRejected() {
    Permit p = submitted();
    p.reject("clerk1", List.of("MISSING_DOCS"));
    assertThatThrownBy(() -> p.approve("clerk1", 90, LocalDate.now(), List.of()))
        .isInstanceOf(PermitRuleViolation.class).hasMessageContaining("REJECTED");
  }

  @Test
  void rejectionRequiresReason() {
    assertThatThrownBy(() -> submitted().reject("clerk1", List.of())).isInstanceOf(PermitRuleViolation.class);
  }

  @Test
  void missingRequiredFieldsRejectedAtSubmit() {
    assertThatThrownBy(() -> Permit.submit(UUID.randomUUID(), "900000001", PermitType.DRIVER, null, null, "CENTER",
        Map.of(), PermitType.DRIVER.defaultRequiredFields()))
        .isInstanceOf(PermitRuleViolation.class).hasMessageContaining("vehiclePlate");
  }

  @Test
  void suspendAndReinstate() {
    Permit p = submitted();
    p.approve("clerk1", 30, LocalDate.now(), List.of());
    p.suspend();
    assertThat(p.status()).isEqualTo(PermitStatus.SUSPENDED);
    p.reinstate();
    assertThat(p.status()).isEqualTo(PermitStatus.ACTIVE);
  }

  @Test
  void terminalStatesHaveNoExit() {
    assertThat(PermitStatus.EXPIRED.isTerminal()).isTrue();
    assertThat(PermitStatus.REVOKED.canMoveTo(PermitStatus.ACTIVE)).isFalse();
  }

  @Test
  void qrSignatureRoundTripAndTamperDetection() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String qr = QrCodec.sign("{\"p\":\"1\"}", kp.getPrivate());
    assertThat(QrCodec.verify(qr, kp.getPublic())).isEqualTo("{\"p\":\"1\"}");
    String tampered = "eyJwIjoiMiJ9" + qr.substring(qr.indexOf('.'));
    assertThat(QrCodec.verify(tampered, kp.getPublic())).isNull();
  }
}
