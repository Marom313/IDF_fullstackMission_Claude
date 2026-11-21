package il.ipp.ingest.verifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The mock world's identities, mirroring {@code il.ipp.common.seed.SeedCatalog} on main (same national-id scheme,
 * same personId derivation, same gates). Kept as a copy because the verifier must stay dependency-free (it runs
 * inside the NiFi image). A test pins the values so a change on either side is caught.
 * <p>
 * Production: the crossing systems send personId / national id from the real registry (GAPS).
 */
final class SeedIds {

    static final int PERSONS = 5000;
    static final List<String> GATES = List.of("GATE-NORTH-1", "GATE-NORTH-2", "GATE-CENTER-1", "GATE-CENTER-2", "GATE-SOUTH-1");

    private SeedIds() { }

    /** Index of a national id in the mock world, or -1. */
    static int indexOf(String id) {
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

    static UUID personId(int i) {
        return UUID.nameUUIDFromBytes(("ipp-person-" + i).getBytes(StandardCharsets.UTF_8));
    }
}
