package il.ipp.registry.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Person aggregate (owned by registry-service). Pure Java – no framework imports (ArchUnit). */
public record Person(
    UUID id, String nationalId, String firstName, String lastName, LocalDate birthDate,
    String magneticCard, LocalDate magneticCardValidTo, SecurityStatus securityStatus,
    String region, UUID employerId, String username, String phone) {

  private static final Pattern NATIONAL_ID = Pattern.compile("\\d{9}");

  public Person {
    Objects.requireNonNull(id, "id");
    if (nationalId == null || !NATIONAL_ID.matcher(nationalId).matches()) {
      throw new IllegalArgumentException("nationalId must be 9 digits");
    }
    Objects.requireNonNull(securityStatus, "securityStatus");
  }

  public int ageOn(LocalDate day) {
    return birthDate == null ? 0 : java.time.Period.between(birthDate, day).getYears();
  }

  public String fullName() {
    return firstName + " " + lastName;
  }
}
