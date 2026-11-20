package il.ipp.ingest.verifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * The "whitening" checks for one incoming crossing batch, in order. Any failure before the row checks
 * quarantines the whole batch; bad rows are dropped individually and reported.
 * <ol>
 *   <li>Size limit of the zip (DoS).</li>
 *   <li>Zip structure: exactly data.csv, manifest.json, manifest.sig; no paths (zip slip); per-entry
 *       uncompressed limit (zip bomb).</li>
 *   <li>Ed25519 signature over the exact bytes of manifest.json (authenticity: who sent it).</li>
 *   <li>SHA-256 of data.csv equals the manifest (integrity: nothing changed on the way), plus size and row count.</li>
 *   <li>Duplicate batch (same SHA-256 already accepted).</li>
 *   <li>Strict UTF-8, exact header, per-row schema: known person, known gate, ENTRY/EXIT, ISO time not in the future.</li>
 * </ol>
 * Production note: steps 1–4 are done first by the organisation's diode + CDR gateway; this is a second check (GAPS).
 */
public final class BatchVerifier {

    public static final String HEADER = "crossingId,nationalId,gate,direction,occurredAt";
    static final String DATA = "data.csv", MANIFEST = "manifest.json", SIGNATURE = "manifest.sig";
    private static final Set<String> ENTRIES = Set.of(DATA, MANIFEST, SIGNATURE);
    private static final Pattern CROSSING_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    /** Limits. Defaults sized for the demo; production values come from the security officer. */
    public record Limits(long maxZipBytes, long maxEntryBytes, int maxRows, Duration maxFutureSkew) {
        public static Limits defaults() {
            return new Limits(5L * 1024 * 1024, 10L * 1024 * 1024, 100_000, Duration.ofMinutes(5));
        }
    }

    public record Result(boolean accepted, String sha256, List<String> reasons, List<Map<String, Object>> crossings,
                         int totalRows, int rejectedRows) {

        static Result quarantine(String sha, String reason) {
            return new Result(false, sha, List.of(reason), List.of(), 0, 0);
        }
    }

    private final PublicKey publicKey;
    private final Path receipts;
    private final Clock clock;
    private final Limits limits;

    public BatchVerifier(PublicKey publicKey, Path receipts, Clock clock, Limits limits) {
        this.publicKey = publicKey;
        this.receipts = receipts;
        this.clock = clock;
        this.limits = limits;
    }

    public static PublicKey readPublicKey(String pem) throws GeneralSecurityException {
        String b64 = pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    public Result verify(byte[] zip, String batchName) {
        String zipSha = sha256(zip);
        // 1. size
        if (zip.length > limits.maxZipBytes()) {
            return Result.quarantine(zipSha, "SIZE_LIMIT: zip is " + zip.length + " bytes, max " + limits.maxZipBytes());
        }
        // 2. structure
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                    return Result.quarantine(zipSha, "ZIP_SLIP: entry with a path is not allowed: " + name);
                }
                if (!ENTRIES.contains(name)) return Result.quarantine(zipSha, "UNEXPECTED_ENTRY: " + name);
                if (files.containsKey(name)) return Result.quarantine(zipSha, "DUPLICATE_ENTRY: " + name);
                byte[] content = readCapped(in, limits.maxEntryBytes());
                if (content == null) {
                    return Result.quarantine(zipSha, "ZIP_BOMB: " + name + " expands beyond " + limits.maxEntryBytes() + " bytes");
                }
                files.put(name, content);
            }
        } catch (ZipException ex) {
            return Result.quarantine(zipSha, "NOT_A_ZIP: " + ex.getMessage());
        } catch (IOException ex) {
            return Result.quarantine(zipSha, "UNREADABLE: " + ex.getMessage());
        }
        if (files.isEmpty()) return Result.quarantine(zipSha, "NOT_A_ZIP: no entries");
        for (String required : ENTRIES) {
            if (!files.containsKey(required)) return Result.quarantine(zipSha, "MISSING_ENTRY: " + required);
        }
        byte[] manifestBytes = files.get(MANIFEST);
        byte[] data = files.get(DATA);
        String dataSha = sha256(data);

        // 3. signature (authenticity)
        try {
            byte[] sig = Base64.getDecoder().decode(new String(files.get(SIGNATURE), StandardCharsets.US_ASCII).trim());
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(manifestBytes);
            if (!verifier.verify(sig)) return Result.quarantine(dataSha, "BAD_SIGNATURE: manifest signature does not verify");
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            return Result.quarantine(dataSha, "BAD_SIGNATURE: " + ex.getMessage());
        }

        // 4. manifest + integrity
        Map<String, String> manifest;
        try {
            manifest = MiniJson.parseFlatObject(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return Result.quarantine(dataSha, "BAD_MANIFEST: " + ex.getMessage());
        }
        for (String field : List.of("fileName", "sizeBytes", "rowCount", "schemaVersion", "createdAt", "sourceId", "sha256")) {
            if (manifest.get(field) == null) return Result.quarantine(dataSha, "BAD_MANIFEST: missing " + field);
        }
        if (!"1".equals(manifest.get("schemaVersion"))) {
            return Result.quarantine(dataSha, "SCHEMA_VERSION: unsupported " + manifest.get("schemaVersion"));
        }
        if (!DATA.equals(manifest.get("fileName"))) return Result.quarantine(dataSha, "BAD_MANIFEST: fileName must be data.csv");
        String declaredSha = manifest.get("sha256").toLowerCase();
        if (!SHA256_HEX.matcher(declaredSha).matches()) return Result.quarantine(dataSha, "BAD_MANIFEST: sha256 is not 64 hex chars");
        if (!MessageDigest.isEqual(declaredSha.getBytes(StandardCharsets.US_ASCII), dataSha.getBytes(StandardCharsets.US_ASCII))) {
            return Result.quarantine(dataSha, "HASH_MISMATCH: manifest " + declaredSha + " but data.csv is " + dataSha);
        }
        if (!String.valueOf(data.length).equals(manifest.get("sizeBytes"))) {
            return Result.quarantine(dataSha, "SIZE_MISMATCH: manifest " + manifest.get("sizeBytes") + " but data.csv is " + data.length);
        }

        // 5. duplicate batch
        if (alreadyAccepted(dataSha)) return Result.quarantine(dataSha, "DUPLICATE_BATCH: already ingested");

        // 6. content
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) {
            return Result.quarantine(dataSha, "BAD_ENCODING: data.csv is not valid UTF-8");
        }
        if (text.startsWith("﻿")) text = text.substring(1);
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
            return Result.quarantine(dataSha, "BAD_HEADER: expected '" + HEADER + "'");
        }
        int rows = lines.size() - 1;
        if (rows > limits.maxRows()) return Result.quarantine(dataSha, "SIZE_LIMIT: " + rows + " rows, max " + limits.maxRows());
        if (!String.valueOf(rows).equals(manifest.get("rowCount"))) {
            return Result.quarantine(dataSha, "ROWCOUNT_MISMATCH: manifest " + manifest.get("rowCount") + " but data.csv has " + rows);
        }

        Instant latest = clock.instant().plus(limits.maxFutureSkew());
        List<Map<String, Object>> crossings = new ArrayList<>();
        List<String> rowErrors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int n = 1; n < lines.size(); n++) {
            String problem = validateRow(lines.get(n), seen, latest, crossings, batchName, dataSha);
            if (problem != null) rowErrors.add("ROW_" + n + ": " + problem);
        }
        if (crossings.isEmpty()) {
            List<String> reasons = new ArrayList<>(List.of("NO_VALID_ROWS"));
            reasons.addAll(rowErrors.stream().limit(10).toList());
            return new Result(false, dataSha, reasons, List.of(), rows, rowErrors.size());
        }
        recordReceipt(dataSha, batchName);
        List<String> reasons = new ArrayList<>();
        reasons.add("ACCEPTED: " + crossings.size() + "/" + rows + " rows");
        reasons.addAll(rowErrors.stream().limit(20).toList());
        return new Result(true, dataSha, reasons, crossings, rows, rowErrors.size());
    }

    private static String validateRow(String line, Set<String> seen, Instant latest, List<Map<String, Object>> out,
                                      String batchName, String batchSha) {
        if (line.contains("\"")) return "QUOTED_FIELD (quoting is not part of schema v1)";
        String[] f = line.split(",", -1);
        if (f.length != 5) return "FIELD_COUNT " + f.length;
        String crossingId = f[0].trim(), nationalId = f[1].trim(), gate = f[2].trim(), direction = f[3].trim(), at = f[4].trim();
        if (!CROSSING_ID.matcher(crossingId).matches()) return "BAD_CROSSING_ID";
        if (!seen.add(crossingId)) return "DUPLICATE_CROSSING_ID " + crossingId;
        int index = SeedIds.indexOf(nationalId);
        if (index < 0) return "UNKNOWN_PERSON";
        if (!SeedIds.GATES.contains(gate)) return "UNKNOWN_GATE " + gate;
        if (!direction.equals("ENTRY") && !direction.equals("EXIT")) return "BAD_DIRECTION " + direction;
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(at);
        } catch (DateTimeParseException ex) {
            return "BAD_TIMESTAMP";
        }
        if (occurredAt.isAfter(latest)) return "FUTURE_TIMESTAMP";

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("crossingId", crossingId);
        c.put("personId", SeedIds.personId(index).toString());
        c.put("nationalId", nationalId);
        c.put("gate", gate);
        c.put("direction", direction);
        c.put("occurredAt", occurredAt.toString());
        c.put("source", "NIFI");
        c.put("batch", batchName);
        c.put("batchSha256", batchSha);
        out.add(c);
        return null;
    }

    /** Reads at most {@code cap} bytes; null when the entry is larger (zip bomb guard: never trust declared sizes). */
    private static byte[] readCapped(InputStream in, long cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > cap) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ receipts (duplicate detection)

    private boolean alreadyAccepted(String sha) {
        if (receipts == null || !Files.exists(receipts)) return false;
        try {
            return Files.readAllLines(receipts).stream().anyMatch(l -> l.startsWith(sha + " "));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read receipts " + receipts, e);
        }
    }

    private void recordReceipt(String sha, String name) {
        if (receipts == null) return;
        try {
            if (receipts.getParent() != null) Files.createDirectories(receipts.getParent());
            try (FileChannel ch = FileChannel.open(receipts, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 FileLock ignored = ch.lock()) {
                ch.write(ByteBuffer.wrap((sha + " " + clock.instant() + " " + name.replaceAll("\\s", "_") + "\n")
                        .getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot write receipts " + receipts, e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
