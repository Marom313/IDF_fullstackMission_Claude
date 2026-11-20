package com.ipp.mockstate;

import com.ipp.mockstate.domain.Chaos;
import com.ipp.mockstate.domain.IdNumbers;
import com.ipp.mockstate.domain.MockPopulation;
import com.ipp.mockstate.domain.MockRecords;
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
        String id = IdNumbers.forIndex(123);
        assertEquals(population.find(id, TODAY), new MockPopulation(42L, 5_000).find(id, TODAY));
    }

    @Test
    void unknownAndOutOfRangeIdsAreEmpty() {
        assertTrue(population.find(IdNumbers.forIndex(5_000), TODAY).isEmpty());   // outside population
        assertTrue(population.find("000000018", TODAY).isEmpty());                 // valid but not mock range
        assertTrue(population.find("123", TODAY).isEmpty());
    }

    @Test
    void distributionIsRealistic() {
        int clear = 0, denied = 0, validCards = 0, adults = 0;
        for (String id : population.ids(0, 5_000)) {
            MockRecords.PersonSummary s = population.find(id, TODAY).orElseThrow();
            if (s.security().status() == MockRecords.SecurityStatus.CLEAR) clear++;
            if (s.security().status() == MockRecords.SecurityStatus.DENIED) denied++;
            if (s.person().magneticCard().valid()) validCards++;
            if (s.person().age() >= 18) adults++;
        }
        assertTrue(clear > 4_000 && clear < 4_500, "clear=" + clear);
        assertTrue(denied > 150 && denied < 400, "denied=" + denied);
        assertTrue(validCards > 4_300, "validCards=" + validCards);
        assertEquals(5_000, adults);
    }

    @Test
    void paging() {
        List<String> page = population.ids(4_990, 50);
        assertEquals(10, page.size());
        assertEquals(IdNumbers.forIndex(4_999), page.getLast());
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
