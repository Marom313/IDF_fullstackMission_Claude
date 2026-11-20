package il.ipp.registry.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonTest {

  private Person person(String nid, LocalDate birth) {
    return new Person(UUID.randomUUID(), nid, "A", "B", birth, "MC-1", LocalDate.now(), SecurityStatus.CLEAR,
        "CENTER", null, null, null);
  }

  @Test
  void rejectsInvalidNationalId() {
    assertThatThrownBy(() -> person("12345", LocalDate.of(1990, 1, 1))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computesAge() {
    assertThat(person("900000001", LocalDate.of(2000, 11, 21)).ageOn(LocalDate.of(2026, 11, 20))).isEqualTo(25);
    assertThat(person("900000001", LocalDate.of(2000, 11, 20)).ageOn(LocalDate.of(2026, 11, 20))).isEqualTo(26);
  }

  @Test
  void avatarIsDeterministicSilhouetteMarkedSpecimen() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    String svg = new String(AvatarGenerator.svgFor(id), StandardCharsets.UTF_8);
    assertThat(svg).contains("<svg").contains("SPECIMEN");
    assertThat(AvatarGenerator.svgFor(id)).isEqualTo(AvatarGenerator.svgFor(id));
  }
}
