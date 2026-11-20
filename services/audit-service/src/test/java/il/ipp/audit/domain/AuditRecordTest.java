package il.ipp.audit.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditRecordTest {
  @Test
  void requiresIdTimeAndAction() {
    assertThatThrownBy(() -> new AuditRecord(null, Instant.now(), "s", "A", null, null, null, null, null, List.of(),
        null, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AuditRecord("1", Instant.now(), "s", null, null, null, null, null, null, List.of(),
        null, null)).isInstanceOf(NullPointerException.class);
  }
}
