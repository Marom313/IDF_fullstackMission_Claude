package com.ipp.mockstate.domain;

import com.ipp.mockstate.domain.MockRecords.CriminalRecord;
import com.ipp.mockstate.domain.MockRecords.LocalizedName;
import com.ipp.mockstate.domain.MockRecords.MagneticCard;
import com.ipp.mockstate.domain.MockRecords.PersonRecord;
import com.ipp.mockstate.domain.MockRecords.PersonSummary;
import com.ipp.mockstate.domain.MockRecords.SecurityRecord;
import com.ipp.mockstate.domain.MockRecords.SecurityStatus;
import com.ipp.mockstate.domain.MockRecords.Severity;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * A synthetic population computed on the fly: person #i is always generated from {@code seed + i},
 * so every service that calls the mock sees the same "facts" for the same ID, with zero storage.
 * <p>
 * IDs and security status follow {@link SeedIds} (the same mock world as main): ~2% PENDING, ~1% DENIED.
 * Other attributes are seeded per person: 92% valid magnetic card, 7% open case.
 * Names are random combinations from short generic lists. They do not describe real people.
 */
public final class MockPopulation {

    private static final String[][] FIRST_MALE = {
            {"מוחמד", "محمد", "Mohammad"}, {"אחמד", "أحمد", "Ahmad"}, {"ח'אלד", "خالد", "Khaled"},
            {"יוסף", "يوسف", "Yousef"}, {"עומר", "عمر", "Omar"}, {"איברהים", "إبراهيم", "Ibrahim"},
            {"מחמוד", "محمود", "Mahmoud"}, {"סאמר", "سامر", "Samer"}, {"ראמי", "رامي", "Rami"}, {"באסם", "باسم", "Basem"}};
    private static final String[][] FIRST_FEMALE = {
            {"פאטמה", "فاطمة", "Fatima"}, {"מרים", "مريم", "Mariam"}, {"לינה", "لينا", "Lina"},
            {"נור", "نور", "Nour"}, {"סמאח", "سماح", "Samah"}, {"רנא", "رنا", "Rana"}};
    private static final String[][] LAST = {
            {"חליל", "خليل", "Khalil"}, {"נאסר", "ناصر", "Nasser"}, {"עודה", "عودة", "Odeh"},
            {"סלאמה", "سلامة", "Salameh"}, {"חמדאן", "حمدان", "Hamdan"}, {"יאסין", "ياسين", "Yassin"},
            {"עבד אללה", "عبد الله", "Abdallah"}, {"מנסור", "منصور", "Mansour"}, {"דאוד", "داود", "Daoud"},
            {"שחאדה", "شحادة", "Shehadeh"}};
    private static final String[] REGIONS = {"JENIN", "NABLUS", "TULKARM", "QALQILYA", "RAMALLAH", "BETHLEHEM", "HEBRON", "JERICHO"};
    private static final String[] SECURITY_REVIEW_CODES = {"R-101", "R-205", "R-310"};
    private static final String[] SECURITY_DENIED_CODES = {"D-400", "D-512"};

    private final long seed;
    private final int size;

    public MockPopulation(long seed, int size) {
        if (size < 1 || size > SeedIds.PERSONS) throw new IllegalArgumentException("size must be 1.." + SeedIds.PERSONS);
        this.seed = seed;
        this.size = size;
    }

    public int size() { return size; }

    public Optional<PersonSummary> find(String idNumber, LocalDate today) {
        int index = SeedIds.indexOf(idNumber);
        if (index < 0 || index >= size) return Optional.empty();
        return Optional.of(generate(index, today));
    }

    /** Page of IDs for seeding other services (registry-service, permit-service). */
    public List<String> ids(int offset, int limit) {
        List<String> out = new ArrayList<>();
        for (int i = Math.max(0, offset); i < Math.min(size, offset + limit); i++) out.add(SeedIds.nationalId(i));
        return out;
    }

    PersonSummary generate(int index, LocalDate today) {
        SplittableRandom r = new SplittableRandom(seed * 1_000_003L + index);
        String id = SeedIds.nationalId(index);

        boolean female = r.nextInt(100) < 12;
        String[] first = female ? FIRST_FEMALE[r.nextInt(FIRST_FEMALE.length)] : FIRST_MALE[r.nextInt(FIRST_MALE.length)];
        String[] last = LAST[r.nextInt(LAST.length)];
        LocalDate birth = today.minusYears(18 + r.nextInt(47)).minusDays(r.nextInt(365));
        int age = Period.between(birth, today).getYears();

        LocalDate issued = today.minusDays(r.nextInt(4 * 365));
        LocalDate validUntil = issued.plusYears(4);                      // magnetic cards are issued for 4 years
        boolean revoked = r.nextInt(100) < 3;
        MagneticCard card = new MagneticCard("MC-" + (1_000_000 + index), issued, validUntil,
                !revoked && !validUntil.isBefore(today));

        SecurityStatus status = SeedIds.securityStatus(index);
        String reason = switch (status) {
            case CLEAR -> null;
            case PENDING -> SECURITY_REVIEW_CODES[r.nextInt(SECURITY_REVIEW_CODES.length)];
            case DENIED -> SECURITY_DENIED_CODES[r.nextInt(SECURITY_DENIED_CODES.length)];
        };

        boolean hasRecord = r.nextInt(100) < 10;
        boolean openCase = r.nextInt(100) < 7;
        Severity severity = openCase ? (r.nextBoolean() ? Severity.LOW : Severity.HIGH) : Severity.NONE;

        PersonRecord person = new PersonRecord(id,
                new LocalizedName(first[0], first[1], first[2]), new LocalizedName(last[0], last[1], last[2]),
                birth, age, female ? "F" : "M", REGIONS[r.nextInt(REGIONS.length)], card,
                "mock/photos/" + (female ? "f" : "m") + "-" + (index % 8) + ".svg");
        return new PersonSummary(person,
                new SecurityRecord(id, status, reason, today.minusDays(r.nextInt(180))),
                new CriminalRecord(id, hasRecord, openCase, severity));
    }
}
