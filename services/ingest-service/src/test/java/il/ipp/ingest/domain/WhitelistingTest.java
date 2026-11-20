package il.ipp.ingest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import il.ipp.ingest.domain.Whitelisting.Manifest;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhitelistingTest {

  @Test
  void signatureVerifiesAndDetectsTampering() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    byte[] manifest = "{\"fileName\":\"crossings_20261120_1200.csv\"}".getBytes(StandardCharsets.UTF_8);
    Signature s = Signature.getInstance("Ed25519");
    s.initSign(kp.getPrivate());
    s.update(manifest);
    String sig = Base64.getEncoder().encodeToString(s.sign());
    assertThat(Whitelisting.signatureValid(manifest, sig, kp.getPublic())).isTrue();
    manifest[5] ^= 1;
    assertThat(Whitelisting.signatureValid(manifest, sig, kp.getPublic())).isFalse();
  }

  @Test
  void envelopeRejectsBadNameAndSizeMismatch() {
    Manifest m = new Manifest("evil.exe", 10, 1, "1", "x", "src", "abc");
    assertThat(Whitelisting.checkEnvelope("evil.exe", 11, m)).extracting(Whitelisting.Rejection::step)
        .contains("TYPE", "SIZE");
  }

  @Test
  void rowsAreValidatedNormalisedAndGetDeterministicIds() {
    List<String> lines = List.of("personId,nationalId,gate,direction,occurredAt,extra",
        "00000000-0000-0000-0000-000000000001,900000005,GATE-CENTER-1,ENTRY,2026-11-20T08:00:00Z,drop-me",
        "not-a-uuid,900000005,GATE-CENTER-1,ENTRY,2026-11-20T08:00:00Z,x",
        "00000000-0000-0000-0000-000000000001,900000005,GATE-MOON,ENTRY,2026-11-20T08:00:00Z,x");
    var r = Whitelisting.validateRows(lines, "sha");
    assertThat(r.valid()).hasSize(1);
    assertThat(r.valid().get(0).record()).doesNotContainKey("extra").containsKey("crossingId");
    assertThat(r.rejected()).hasSize(2);
    assertThat(Whitelisting.validateRows(lines, "sha").valid().get(0).crossingId())
        .isEqualTo(r.valid().get(0).crossingId());
  }

  @Test
  void sha256IsHex() {
    assertThat(Whitelisting.sha256("abc".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
