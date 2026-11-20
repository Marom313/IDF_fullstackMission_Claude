package com.ipp.mockstate.domain;

import java.time.LocalDate;

/** Response shapes of the mock "state" interfaces. All values are synthetic. */
public final class MockRecords {

    private MockRecords() { }

    public record LocalizedName(String he, String ar, String en) { }

    public record MagneticCard(String number, LocalDate issuedOn, LocalDate validUntil, boolean valid) { }

    /** Population registry. */
    public record PersonRecord(String idNumber, LocalizedName firstName, LocalizedName lastName, LocalDate birthDate,
                               int age, String gender, String region, MagneticCard magneticCard, String photoKey) { }

    /** Security status. {@code reasonCode} is an opaque code, never free text, as in real classified interfaces. */
    public record SecurityRecord(String idNumber, SecurityStatus status, String reasonCode, LocalDate reviewedOn) { }

    public enum SecurityStatus { CLEAR, REVIEW, DENIED }

    public record CriminalRecord(String idNumber, boolean hasRecord, boolean hasOpenCase, Severity caseSeverity) { }

    public enum Severity { NONE, LOW, HIGH }

    /** All three in one call, the shape eligibility-service needs on its hot path. */
    public record PersonSummary(PersonRecord person, SecurityRecord security, CriminalRecord criminal) { }
}
