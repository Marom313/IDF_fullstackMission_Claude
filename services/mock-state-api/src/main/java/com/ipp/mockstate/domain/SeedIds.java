package com.ipp.mockstate.domain;

/**
 * The "mock world" shared with the rest of IPP. Mirrors {@code com.ipp.common.seed.SeedCatalog} on main
 * (single source of truth for synthetic people): same national-id scheme and same security-status rule,
 * so permit-service, eligibility-service and this mock agree about every person.
 * <p>
 * Not a Maven dependency on java-common on purpose: that library auto-configures security, Kafka and JDBC,
 * which a stateless mock does not need. Consistency is guarded by a test that compares both on all 5,000 people.
 *
 * <pre>
 *   index 0..4   → 900000001 … 900000005  (demo people with memorable ids)
 *   index 5..    → 98 + 7 digits           (e.g. 980000123)
 * </pre>
 */
public final class SeedIds {

    public static final int PERSONS = 5000;

    private SeedIds() { }

    public static String nationalId(int i) {
        return i < 5 ? "90000000" + (i + 1) : String.format("98%07d", i);
    }

    /** Population index for a national id, or -1 when the id is not part of the mock world. */
    public static int indexOf(String id) {
        if (id == null || !id.matches("\\d{9}")) return -1;
        if (id.startsWith("90000000")) {
            int i = id.charAt(8) - '1';
            return i >= 0 && i < 5 ? i : -1;
        }
        if (id.startsWith("98")) {
            int i = Integer.parseInt(id.substring(2));
            return i >= 5 && i < PERSONS ? i : -1;
        }
        return -1;
    }

    /** Same rule as SeedCatalog.securityStatus(i) on main. */
    public static MockRecords.SecurityStatus securityStatus(int i) {
        if (i == 3 || i % 50 == 7) return MockRecords.SecurityStatus.PENDING;
        if (i > 4 && i % 97 == 11) return MockRecords.SecurityStatus.DENIED;
        return MockRecords.SecurityStatus.CLEAR;
    }
}
