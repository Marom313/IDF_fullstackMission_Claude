package il.ipp.ingest.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The whitelisting checks, pure and testable. In production steps 2–4 run in the organisation's
 * diode/CDR gateway and this pipeline RE-verifies them (defense in depth). See ADR 0007.
 */
public final class Whitelisting {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  public static final int MAX_ROWS = 100_000;
  public static final Set<String> GATES =
      Set.of("GATE-NORTH-1", "GATE-NORTH-2", "GATE-CENTER-1", "GATE-CENTER-2", "GATE-SOUTH-1");
  public static final List<String> SCHEMA = List.of("personId", "nationalId", "gate", "direction", "occurredAt");
  private static final Pattern NID = Pattern.compile("\\d{9}");
  private static final Pattern FILE = Pattern.compile("crossings_\\d{8}_\\d{4}\\.csv");

  private Whitelisting() {}

  /** Manifest fields we rely on (parsed by the adapter from manifest.json). */
  public record Manifest(String fileName, long sizeBytes, int rowCount, String schemaVersion, String createdAt,
                         String sourceId, String sha256) {}

  public record Rejection(String step, String reason) {}

  /** Step 2: name/type/size before touching content. */
  public static List<Rejection> checkEnvelope(String fileName, long size, Manifest m) {
    List<Rejection> r = new ArrayList<>();
    if (!FILE.matcher(fileName).matches()) {
      r.add(new Rejection("TYPE", "file name must match crossings_YYYYMMDD_HHMM.csv"));
    }
    if (size <= 0 || size > MAX_BYTES) {
      r.add(new Rejection("SIZE", "size " + size + " outside 1.." + MAX_BYTES));
    }
    if (m.sizeBytes() != size) {
      r.add(new Rejection("SIZE", "manifest sizeBytes " + m.sizeBytes() + " != actual " + size));
    }
    if (!fileName.equals(m.fileName())) {
      r.add(new Rejection("MANIFEST", "manifest fileName does not match"));
    }
    if (!"1".equals(m.schemaVersion())) {
      r.add(new Rejection("SCHEMA", "unsupported schemaVersion " + m.schemaVersion()));
    }
    return r;
  }

  /** Step 3: Ed25519 signature over the exact manifest bytes. */
  public static boolean signatureValid(byte[] manifestBytes, String signatureB64, PublicKey key) {
    try {
      Signature s = Signature.getInstance("Ed25519");
      s.initVerify(key);
      s.update(manifestBytes);
      return s.verify(Base64.getDecoder().decode(signatureB64.trim()));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return false;
    }
  }

  /** Step 4: SHA-256 of the data file (hex, lowercase). */
  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public record ValidRow(String crossingId, Map<String, String> record) {}

  public record RowResult(List<ValidRow> valid, List<String> rejected) {}

  /**
   * Steps 6–7: validate each row against schema v1, normalise, drop unknown columns.
   * crossingId is deterministic (file hash + line) so a re-sent row is deduplicated downstream.
   */
  public static RowResult validateRows(List<String> lines, String fileSha) {
    List<ValidRow> valid = new ArrayList<>();
    List<String> rejected = new ArrayList<>();
    if (lines.isEmpty()) {
      return new RowResult(valid, List.of("0: empty file"));
    }
    String[] header = lines.get(0).trim().split(",");
    Map<String, Integer> idx = new LinkedHashMap<>();
    for (int i = 0; i < header.length; i++) {
      idx.put(header[i].trim(), i);
    }
    for (String col : SCHEMA) {
      if (!idx.containsKey(col)) {
        return new RowResult(valid, List.of("0: missing column " + col));
      }
    }
    for (int n = 1; n < lines.size(); n++) {
      String line = lines.get(n);
      if (line.isBlank()) {
        continue;
      }
      String[] c = line.split(",", -1);
      try {
        Map<String, String> rec = new LinkedHashMap<>();
        for (String col : SCHEMA) {
          rec.put(col, c[idx.get(col)].trim());
        }
        UUID.fromString(rec.get("personId"));
        require(NID.matcher(rec.get("nationalId")).matches(), "nationalId must be 9 digits");
        require(GATES.contains(rec.get("gate")), "unknown gate " + rec.get("gate"));
        require(Set.of("ENTRY", "EXIT").contains(rec.get("direction")), "direction must be ENTRY|EXIT");
        rec.put("occurredAt", Instant.parse(rec.get("occurredAt")).toString());
        String id = UUID.nameUUIDFromBytes((fileSha + ":" + n).getBytes(StandardCharsets.UTF_8)).toString();
        rec.put("crossingId", id);
        valid.add(new ValidRow(id, rec));
      } catch (ArrayIndexOutOfBoundsException e) {
        rejected.add(n + ": wrong number of columns");
      } catch (IllegalArgumentException | DateTimeParseException e) {
        rejected.add(n + ": " + e.getMessage());
      }
    }
    return new RowResult(valid, rejected);
  }

  public static PublicKey publicKeyFromPem(String pem) throws GeneralSecurityException {
    String b64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
  }

  private static void require(boolean ok, String msg) {
    if (!ok) {
      throw new IllegalArgumentException(msg);
    }
  }
}
