package il.ipp.common;

import static org.assertj.core.api.Assertions.assertThat;

import il.ipp.common.logging.PiiValueMasker;
import org.junit.jupiter.api.Test;

class PiiValueMaskerTest {

  @Test
  void masksNationalIdKeepingLastTwoDigits() {
    assertThat(PiiValueMasker.maskText("eligibility check for 123456789 ok"))
        .isEqualTo("eligibility check for *******89 ok");
  }

  @Test
  void masksPhone() {
    assertThat(PiiValueMasker.maskText("call 052-123-4567")).isEqualTo("call 052-***-****");
  }

  @Test
  void leavesTimestampNanosAlone() {
    String s = "2026-11-20T13:40:51.123456738Z";
    assertThat(PiiValueMasker.maskText(s)).isEqualTo(s);
  }

  @Test
  void leavesUuidsAndShortNumbersAlone() {
    String s = "permit 7f3c2a1e-0000-4000-8000-000000000001 qty 42";
    assertThat(PiiValueMasker.maskText(s)).isEqualTo(s);
  }
}
