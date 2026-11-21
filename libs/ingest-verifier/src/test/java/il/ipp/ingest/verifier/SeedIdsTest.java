package il.ipp.ingest.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pins the copy of main's SeedCatalog id scheme (libs/java-common il.ipp.common.seed.SeedCatalog). */
class SeedIdsTest {

    /** Same formula as SeedCatalog.nationalId(i). */
    private static String nationalId(int i) {
        return i < 5 ? "90000000" + (i + 1) : String.format("98%07d", i);
    }

    @Test
    void everySeedPersonRoundTrips() {
        for (int i = 0; i < SeedIds.PERSONS; i++) assertEquals(i, SeedIds.indexOf(nationalId(i)), nationalId(i));
    }

    @Test
    void unknownIdsAreRejected() {
        for (String id : new String[] {"900000000", "900000006", "980000004", "980005000", "123456789", "98000000x", "", null}) {
            assertEquals(-1, SeedIds.indexOf(id), String.valueOf(id));
        }
    }

    @Test
    void personIdMatchesMainsDerivation() {
        assertEquals(java.util.UUID.nameUUIDFromBytes("ipp-person-0".getBytes(java.nio.charset.StandardCharsets.UTF_8)), SeedIds.personId(0));
        assertEquals(5, SeedIds.GATES.size());
    }
}
