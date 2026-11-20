package il.ipp.ingest.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class BatchVerifierTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private static final KeyPair KEYS = keys();
    private static final KeyPair OTHER_KEYS = keys();

    private static final String CSV = BatchVerifier.HEADER + "\n"
            + "C-1,900000001,GATE-NORTH-1,ENTRY,2026-11-20T06:00:00Z\n"
            + "C-2,980000005,GATE-SOUTH-1,EXIT,2026-11-20T10:30:00Z\n";

    private BatchVerifier verifier(Path receipts) {
        return new BatchVerifier(KEYS.getPublic(), receipts, CLOCK, BatchVerifier.Limits.defaults());
    }

    @Test
    void acceptsAValidSignedBatchAndMapsPersonIds() throws Exception {
        var r = verifier(null).verify(batch(CSV, KEYS, null), "b1.zip");
        assertTrue(r.accepted(), r.reasons().toString());
        assertEquals(2, r.crossings().size());
        assertEquals(SeedIds.personId(0).toString(), r.crossings().get(0).get("personId"));
        assertEquals(SeedIds.personId(5).toString(), r.crossings().get(1).get("personId"));
        assertEquals("NIFI", r.crossings().get(0).get("source"));
    }

    @Test
    void tamperedDataFailsTheHash() throws Exception {
        String tampered = CSV.replace("GATE-SOUTH-1", "GATE-NORTH-2");
        var r = verifier(null).verify(batch(CSV, KEYS, tampered), "b.zip");
        assertFalse(r.accepted());
        assertTrue(r.reasons().getFirst().startsWith("HASH_MISMATCH"), r.reasons().toString());
    }

    @Test
    void wrongSigningKeyIsRejected() throws Exception {
        var r = verifier(null).verify(batch(CSV, OTHER_KEYS, null), "b.zip");
        assertFalse(r.accepted());
        assertTrue(r.reasons().getFirst().startsWith("BAD_SIGNATURE"), r.reasons().toString());
    }

    @Test
    void sameBatchTwiceIsADuplicate() throws Exception {
        Path receipts = Files.createTempDirectory("ipp").resolve(".receipts");
        byte[] zip = batch(CSV, KEYS, null);
        assertTrue(verifier(receipts).verify(zip, "b.zip").accepted());
        var second = verifier(receipts).verify(zip, "b-again.zip");
        assertFalse(second.accepted());
        assertTrue(second.reasons().getFirst().startsWith("DUPLICATE_BATCH"), second.reasons().toString());
    }

    @Test
    void badRowsAreDroppedAndReportedGoodRowsPass() throws Exception {
        String csv = CSV
                + "C-3,123456789,GATE-NORTH-1,ENTRY,2026-11-20T06:00:00Z\n"     // unknown person
                + "C-4,900000002,GATE-MARS,ENTRY,2026-11-20T06:00:00Z\n"        // unknown gate
                + "C-5,900000003,GATE-NORTH-1,SIDEWAYS,2026-11-20T06:00:00Z\n"  // bad direction
                + "C-6,900000004,GATE-NORTH-1,ENTRY,yesterday\n"                // bad time
                + "C-7,900000004,GATE-NORTH-1,ENTRY,2027-01-01T00:00:00Z\n"     // future
                + "C-1,900000005,GATE-NORTH-1,ENTRY,2026-11-20T06:00:00Z\n";    // duplicate crossingId
        var r = verifier(null).verify(batch(csv, KEYS, null), "b.zip");
        assertTrue(r.accepted());
        assertEquals(2, r.crossings().size());
        assertEquals(6, r.rejectedRows());
        String all = String.join("|", r.reasons());
        for (String code : new String[] {"UNKNOWN_PERSON", "UNKNOWN_GATE", "BAD_DIRECTION", "BAD_TIMESTAMP", "FUTURE_TIMESTAMP", "DUPLICATE_CROSSING_ID"}) {
            assertTrue(all.contains(code), "missing " + code + " in " + all);
        }
    }

    @Test
    void zipSlipAndExtraEntriesAreRejected() throws Exception {
        Map<String, byte[]> entries = signedEntries(CSV, KEYS);
        entries.put("../../etc/cron.d/x", "boom".getBytes(StandardCharsets.UTF_8));
        var r = verifier(null).verify(zip(entries), "b.zip");
        assertFalse(r.accepted());
        assertTrue(r.reasons().getFirst().startsWith("ZIP_SLIP"), r.reasons().toString());

        Map<String, byte[]> extra = signedEntries(CSV, KEYS);
        extra.put("run.sh", "rm -rf /".getBytes(StandardCharsets.UTF_8));
        var r2 = verifier(null).verify(zip(extra), "b.zip");
        assertTrue(r2.reasons().getFirst().startsWith("UNEXPECTED_ENTRY"), r2.reasons().toString());
    }

    @Test
    void zipBombIsStoppedWithoutExpandingEverything() throws Exception {
        Map<String, byte[]> entries = signedEntries(CSV, KEYS);
        entries.put("data.csv", new byte[11 * 1024 * 1024]);   // zeros compress to a few KB
        byte[] zip = zip(entries);
        assertTrue(zip.length < 100_000, "test zip should be small: " + zip.length);
        var r = verifier(null).verify(zip, "b.zip");
        assertFalse(r.accepted());
        assertTrue(r.reasons().getFirst().startsWith("ZIP_BOMB"), r.reasons().toString());
    }

    @Test
    void notAZipAndMissingEntriesAreQuarantined() throws Exception {
        assertFalse(verifier(null).verify("hello".getBytes(StandardCharsets.UTF_8), "x.zip").accepted());
        Map<String, byte[]> entries = signedEntries(CSV, KEYS);
        entries.remove("manifest.sig");
        var r = verifier(null).verify(zip(entries), "b.zip");
        assertTrue(r.reasons().getFirst().startsWith("MISSING_ENTRY"), r.reasons().toString());
    }

    @Test
    void wrongHeaderAndRowCountAreQuarantined() throws Exception {
        String csv = "id,who,where,dir,when\nC-1,900000001,GATE-NORTH-1,ENTRY,2026-11-20T06:00:00Z\n";
        var r = verifier(null).verify(batch(csv, KEYS, null), "b.zip");
        assertTrue(r.reasons().getFirst().startsWith("BAD_HEADER"), r.reasons().toString());

        Map<String, byte[]> entries = signedEntries(CSV, KEYS, 7);
        var r2 = verifier(null).verify(zip(entries), "b.zip");
        assertTrue(r2.reasons().getFirst().startsWith("ROWCOUNT_MISMATCH"), r2.reasons().toString());
    }

    @Test
    void cliPrintsKafkaLinesAndExitCodes() throws Exception {
        Path dir = Files.createTempDirectory("ipp");
        Path pem = dir.resolve("pub.pem");
        Files.writeString(pem, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(KEYS.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
        String[] args = {"--key", pem.toString(), "--receipts", dir.resolve(".receipts").toString(), "--name", "b1.zip"};

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code = IngestVerifier.run(args, new ByteArrayInputStream(batch(CSV, KEYS, null)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), CLOCK);
        assertEquals(IngestVerifier.ACCEPTED, code);
        String[] lines = out.toString(StandardCharsets.UTF_8).trim().split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("{\"_topic\":\"crossing.event.recorded.v1\",\"_key\":\"" + SeedIds.personId(0) + "\""), lines[0]);
        assertTrue(lines[2].contains("\"_topic\":\"audit.ingest.v1\"") && lines[2].contains("INGEST_ACCEPTED"), lines[2]);

        // same batch again → quarantined (exit 3) with only the audit line
        var out2 = new ByteArrayOutputStream();
        int again = IngestVerifier.run(args, new ByteArrayInputStream(batch(CSV, KEYS, null)),
                new PrintStream(out2, true, StandardCharsets.UTF_8), new PrintStream(new ByteArrayOutputStream()), CLOCK);
        assertEquals(IngestVerifier.QUARANTINED, again);
        String only = out2.toString(StandardCharsets.UTF_8).trim();
        assertFalse(only.contains("\n"));
        assertTrue(only.contains("INGEST_QUARANTINED") && only.contains("DUPLICATE_BATCH"), only);

        assertEquals(IngestVerifier.USAGE, IngestVerifier.run(new String[0], new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), CLOCK));
    }

    // ------------------------------------------------------------------ helpers

    private static KeyPair keys() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A batch whose manifest describes {@code csv}; if {@code shipped} is set, that content is zipped instead (tamper). */
    static byte[] batch(String csv, KeyPair keys, String shipped) throws Exception {
        Map<String, byte[]> entries = signedEntries(csv, keys);
        if (shipped != null) entries.put("data.csv", shipped.getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    static Map<String, byte[]> signedEntries(String csv, KeyPair keys) throws Exception {
        long rows = csv.lines().count() - 1;
        return signedEntries(csv, keys, rows);
    }

    static Map<String, byte[]> signedEntries(String csv, KeyPair keys, long rowCount) throws Exception {
        byte[] data = csv.getBytes(StandardCharsets.UTF_8);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileName", "data.csv");
        m.put("sha256", BatchVerifier.sha256(data));
        m.put("sizeBytes", data.length);
        m.put("rowCount", rowCount);
        m.put("schemaVersion", 1);
        m.put("createdAt", "2026-11-20T11:00:00Z");
        m.put("sourceId", "MOCK-GATE-SYSTEM");
        byte[] manifest = MiniJson.write(m).getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(keys.getPrivate());
        s.update(manifest);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("data.csv", data);
        entries.put("manifest.json", manifest);
        entries.put("manifest.sig", Base64.getEncoder().encode(s.sign()));
        return entries;
    }

    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(bos)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
