package com.ipp.mockstate;

import com.ipp.mockstate.domain.Chaos;
import com.ipp.mockstate.domain.IdNumbers;
import com.ipp.mockstate.domain.MockPopulation;
import com.ipp.mockstate.domain.MockRecords;
import com.ipp.mockstate.domain.SeedIds;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPopulationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private final MockPopulation population = new MockPopulation(42L, 5_000);

    @Test
    void checkDigitAlgorithm() {
        assertTrue(IdNumbers.isValid("000000018"));
        assertFalse(IdNumbers.isValid("000000019"));
        assertFalse(IdNumbers.isValid("12ab56789"));
        assertFalse(IdNumbers.isValid(null));
        for (int i = 0; i < 1_000; i++) {
            String id = IdNumbers.forIndex(i);
            assertEquals(9, id.length());
            assertTrue(IdNumbers.isValid(id), id);
            assertEquals(i, IdNumbers.indexOf(id));
        }
    }

    @Test
    void sameIdAlwaysSameFacts() {
        String id = SeedIds.nationalId(123);
        assertEquals(population.find(id, TODAY), new MockPopulation(42L, 5_000).find(id, TODAY));
    }

    @Test
    void unknownAndOutOfRangeIdsAreEmpty() {
        assertTrue(population.find("980005000", TODAY).isEmpty());                 // outside population
        assertTrue(population.find("000000018", TODAY).isEmpty());                 // not in the mock world
        assertTrue(population.find("123", TODAY).isEmpty());
    }

    @Test
    void distributionIsRealistic() {
        int clear = 0, pending = 0, denied = 0, validCards = 0, adults = 0;
        for (String id : population.ids(0, 5_000)) {
            MockRecords.PersonSummary s = population.find(id, TODAY).orElseThrow();
            if (s.security().status() == MockRecords.SecurityStatus.CLEAR) clear++;
            if (s.security().status() == MockRecords.SecurityStatus.PENDING) pending++;
            if (s.security().status() == MockRecords.SecurityStatus.DENIED) denied++;
            if (s.person().magneticCard().valid()) validCards++;
            if (s.person().age() >= 18) adults++;
        }
        assertEquals(5_000, clear + pending + denied);
        assertTrue(pending > 50 && pending < 150, "pending=" + pending);
        assertTrue(denied > 20 && denied < 80, "denied=" + denied);
        assertTrue(validCards > 4_300, "validCards=" + validCards);
        assertEquals(5_000, adults);
    }

    @Test
    void paging() {
        List<String> page = population.ids(4_990, 50);
        assertEquals(10, page.size());
        assertEquals("980004999", page.getLast());
        assertEquals("900000001", population.ids(0, 1).getFirst());
    }

    /** Pinned values of main's SeedCatalog: if main changes its rule, this fails and someone aligns the mock. */
    @Test
    void agreesWithMainSeedCatalog() {
        assertEquals(MockRecords.SecurityStatus.CLEAR, status("900000001"));    // worker1 → GREEN on main
        assertEquals(MockRecords.SecurityStatus.PENDING, status("900000004"));  // demo person 3 → REFER_TO_CLERK
        assertEquals(MockRecords.SecurityStatus.PENDING, status("980000057"));  // 57 % 50 == 7
        assertEquals(MockRecords.SecurityStatus.DENIED, status("980000108"));   // 108 % 97 == 11
        assertEquals(0, SeedIds.indexOf("900000001"));
        assertEquals(1234, SeedIds.indexOf("980001234"));
        assertEquals(-1, SeedIds.indexOf("980000003"));                         // 98-range starts at index 5
    }

    private MockRecords.SecurityStatus status(String id) {
        return population.find(id, TODAY).orElseThrow().security().status();
    }

    @Test
    void chaosSettingsValidation() {
        Chaos chaos = new Chaos();
        assertEquals(Chaos.Effect.PROCEED, chaos.roll());
        chaos.set(new Chaos.Settings(0, 0, 0, true));
        assertEquals(Chaos.Effect.FAIL, chaos.roll());
        chaos.reset();
        assertEquals(0, chaos.delayMs());
        assertThrows(IllegalArgumentException.class, () -> new Chaos.Settings(0, 0, 1.5, false));
    }
}
