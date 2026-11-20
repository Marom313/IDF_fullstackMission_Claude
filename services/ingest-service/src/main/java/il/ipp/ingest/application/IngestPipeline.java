package il.ipp.ingest.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.events.Topics;
import il.ipp.ingest.domain.Whitelisting;
import il.ipp.ingest.domain.Whitelisting.Manifest;
import il.ipp.ingest.domain.Whitelisting.Rejection;
import il.ipp.ingest.domain.Whitelisting.RowResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The 9-step whitelisting pipeline (same order as the NiFi design, ADR 0007):
 * 1 list/fetch · 2 size/type · 3 manifest signature · 4 SHA-256 · 5 dedup · 6 schema · 7 normalise ·
 * 8 publish to crossing.event.recorded.v1 (key=personId) · 9 archive + audit.ingest.v1.
 * Every rejection is quarantined with a .reason file and audited.
 */
@Component
public class IngestPipeline {
  private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);
  private final Path inbox;
  private final Path quarantine;
  private final Path archive;
  private final PublicKey key;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;

  public IngestPipeline(@Value("${ipp.ingest.dir:/ingest}") String root,
                        @Value("${ipp.ingest.public-key:/keys/ingest.pub.pem}") String keyFile,
                        KafkaTemplate<String, String> kafka, ObjectMapper json) throws Exception {
    this.inbox = Path.of(root, "inbox");
    this.quarantine = Path.of(root, "quarantine");
    this.archive = Path.of(root, "archive");
    this.key = Whitelisting.publicKeyFromPem(Files.readString(Path.of(keyFile)));
    this.kafka = kafka;
    this.json = json;
    Files.createDirectories(quarantine);
    Files.createDirectories(archive);
  }

  /** Step 1: ListFile/FetchFile – a batch is ready only when the data file, manifest and signature are all present. */
  @Scheduled(fixedDelayString = "${ipp.ingest.poll-ms:3000}", initialDelay = 5000)
  public void poll() {
    try (Stream<Path> s = Files.list(inbox)) {
      for (Path csv : s.filter(p -> p.toString().endsWith(".csv")).sorted().toList()) {
        Path manifest = sibling(csv, ".manifest.json");
        Path sig = sibling(csv, ".manifest.sig");
        if (Files.exists(manifest) && Files.exists(sig)) {
          process(csv, manifest, sig);
        }
      }
    } catch (IOException e) {
      log.warn("inbox scan failed: {}", e.toString());
    }
  }

  void process(Path csv, Path manifestPath, Path sigPath) {
    String name = csv.getFileName().toString();
    try {
      byte[] data = Files.readAllBytes(csv);
      byte[] manifestBytes = Files.readAllBytes(manifestPath);
      Manifest m = json.readValue(manifestBytes, Manifest.class);

      List<Rejection> env = Whitelisting.checkEnvelope(name, data.length, m);                       // 2
      if (!env.isEmpty()) {
        reject(csv, manifestPath, sigPath, env.get(0), m, 0, 0);
        return;
      }
      if (!Whitelisting.signatureValid(manifestBytes, Files.readString(sigPath), key)) {           // 3
        reject(csv, manifestPath, sigPath, new Rejection("SIGNATURE", "Ed25519 signature on manifest invalid"), m, 0, 0);
        return;
      }
      String sha = Whitelisting.sha256(data);                                                     // 4
      if (!sha.equalsIgnoreCase(m.sha256())) {
        reject(csv, manifestPath, sigPath, new Rejection("HASH", "SHA-256 mismatch: " + sha), m, 0, 0);
        return;
      }
      Path archived = archive.resolve(sha + ".csv");                                               // 5
      if (Files.exists(archived)) {
        reject(csv, manifestPath, sigPath, new Rejection("DUPLICATE", "already ingested (sha " + sha + ")"), m, 0, 0);
        return;
      }
      List<String> lines = new String(data, StandardCharsets.UTF_8).lines().toList();
      if (lines.size() - 1 > Whitelisting.MAX_ROWS || lines.size() - 1 != m.rowCount()) {
        reject(csv, manifestPath, sigPath, new Rejection("ROWCOUNT", "rows " + (lines.size() - 1) + " vs manifest "
            + m.rowCount()), m, 0, 0);
        return;
      }
      RowResult rows = Whitelisting.validateRows(lines, sha);                                      // 6–7
      List<CompletableFuture<?>> sends = new ArrayList<>();
      for (var r : rows.valid()) {                                                                 // 8
        sends.add(kafka.send(Topics.CROSSING_RECORDED, r.record().get("personId"), json.writeValueAsString(
            Map.of("crossingId", r.crossingId(), "personId", r.record().get("personId"), "nationalId",
                r.record().get("nationalId"), "gate", r.record().get("gate"), "direction", r.record().get("direction"),
                "occurredAt", r.record().get("occurredAt"), "source", "INGEST:" + m.sourceId()))));
      }
      CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
      Files.move(csv, archived, StandardCopyOption.ATOMIC_MOVE);                                   // 9
      Files.move(manifestPath, archive.resolve(sha + ".manifest.json"), StandardCopyOption.REPLACE_EXISTING);
      Files.move(sigPath, archive.resolve(sha + ".manifest.sig"), StandardCopyOption.REPLACE_EXISTING);
      if (!rows.rejected().isEmpty()) {
        Files.writeString(quarantine.resolve(name + ".rejected-rows.txt"), String.join("\n", rows.rejected()));
      }
      audit(m, "ACCEPTED", null, rows.valid().size(), rows.rejected().size(), sha);
      log.info("ingested {}: {} rows published, {} rejected", name, rows.valid().size(), rows.rejected().size());
    } catch (Exception e) {
      reject(csv, manifestPath, sigPath, new Rejection("ERROR", e.getClass().getSimpleName() + ": " + e.getMessage()),
          null, 0, 0);
    }
  }

  private void reject(Path csv, Path manifest, Path sig, Rejection why, Manifest m, int ok, int bad) {
    try {
      for (Path p : List.of(csv, manifest, sig)) {
        if (Files.exists(p)) {
          Files.move(p, quarantine.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Files.writeString(quarantine.resolve(csv.getFileName() + ".reason"), why.step() + ": " + why.reason());
      log.warn("quarantined {}: {} – {}", csv.getFileName(), why.step(), why.reason());
      audit(m, "QUARANTINED", why.step() + ": " + why.reason(), ok, bad, m == null ? null : m.sha256());
    } catch (IOException e) {
      log.error("could not quarantine {}", csv, e);
    }
  }

  private void audit(Manifest m, String decision, String reason, int ok, int bad, String sha) {
    try {
      Map<String, Object> p = new HashMap<>();
      p.put("service", "ingest-service");
      p.put("action", "INGEST_BATCH");
      p.put("entityType", "INGEST_FILE");
      p.put("entityId", m == null ? "unknown" : m.fileName());
      p.put("actor", m == null ? "unknown" : "source:" + m.sourceId());
      p.put("decision", decision);
      p.put("reasons", reason == null ? List.of("rows:" + ok, "rejectedRows:" + bad, "sha256:" + sha)
          : List.of(reason));
      p.put("policyVersion", "ingest-whitelist-v1");
      EventEnvelope env = EventEnvelope.of("AuditRecord", null, "ingest", p);
      kafka.send(Topics.AUDIT_INGEST, "ingest", json.writeValueAsString(env));
    } catch (Exception e) {
      log.warn("audit publish failed: {}", e.toString());
    }
  }

  private static Path sibling(Path csv, String suffix) {
    String n = csv.getFileName().toString();
    return csv.resolveSibling(n.substring(0, n.length() - 4) + suffix);
  }
}
