package il.ipp.permits.domain;

import java.util.List;

public enum PermitType {
  WORK_ENTRY(List.of("idNumber", "photo", "magneticCard", "employerId", "workRegion")),
  DRIVER(List.of("idNumber", "photo", "magneticCard", "vehiclePlate", "cargoType", "destinationRegion")),
  MEDICAL(List.of("idNumber", "photo", "hospitalName", "appointmentDate"));

  /** Fallback required fields when policy-service is unavailable (policy normally decides). */
  private final List<String> defaultRequiredFields;

  PermitType(List<String> fields) {
    this.defaultRequiredFields = fields;
  }

  public List<String> defaultRequiredFields() {
    return defaultRequiredFields;
  }
}
