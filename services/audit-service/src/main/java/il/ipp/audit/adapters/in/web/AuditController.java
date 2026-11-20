package il.ipp.audit.adapters.in.web;

import il.ipp.audit.domain.AuditPorts.AuditStore;
import il.ipp.audit.domain.AuditPorts.DeadLetter;
import il.ipp.audit.domain.AuditPorts.DeadLetterStore;
import il.ipp.audit.domain.AuditRecord;
import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AuditController {
  private final AuditStore audit;
  private final DeadLetterStore dlt;
  private final KafkaTemplate<String, String> kafka;
  private final Path quarantine;

  AuditController(AuditStore audit, DeadLetterStore dlt, KafkaTemplate<String, String> kafka,
                  @Value("${ipp.ingest.quarantine-dir:/ingest/quarantine}") String quarantine) {
    this.audit = audit;
    this.dlt = dlt;
    this.kafka = kafka;
    this.quarantine = Path.of(quarantine);
  }

  record AuditPage(List<AuditRecord> items, Instant nextBefore) {}

  @GetMapping("/audit")
  AuditPage search(@RequestParam(required = false) String entityId, @RequestParam(required = false) String personId,
                   @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
                   @RequestParam(required = false) Instant before, @RequestParam(defaultValue = "50") int limit) {
    requireAuditor();
    int l = Math.min(Math.max(limit, 1), 200);
    List<AuditRecord> items = audit.search(entityId, personId, actor, action, before, l);
    return new AuditPage(items, items.size() == l ? items.get(items.size() - 1).at() : null);
  }

  @GetMapping("/audit/stats")
  Map<String, Long> stats() {
    return audit.countByAction();
  }

  @GetMapping("/admin/dlt")
  List<DeadLetter> deadLetters() {
    requireAdmin();
    return dlt.recent(100);
  }

  /** Replay = re-publish the original record to its original topic (consumers are idempotent). */
  @PostMapping("/admin/dlt/{id}/replay")
  Map<String, Object> replay(@PathVariable String id) throws Exception {
    requireAdmin();
    DeadLetter d = dlt.find(id).orElseThrow(() -> DomainException.notFound("Dead letter"));
    kafka.send(d.originalTopic(), d.key(), d.value()).get();
    dlt.markReplayed(id);
    return Map.of("replayed", true, "topic", d.originalTopic());
  }

  record QuarantinedFile(String name, long sizeBytes, Instant modifiedAt, String reason) {}

  /** Files rejected by the NiFi whitelisting pipeline (read-only mount). */
  @GetMapping("/admin/quarantine")
  List<QuarantinedFile> quarantine() throws IOException {
    requireAdmin();
    if (!Files.isDirectory(quarantine)) {
      return List.of();
    }
    try (Stream<Path> s = Files.list(quarantine)) {
      return s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().startsWith("."))
          .filter(p -> !p.getFileName().toString().endsWith(".reason"))
          .map(p -> {
            try {
              Path reasonFile = p.resolveSibling(p.getFileName() + ".reason");
              String reason = Files.exists(reasonFile) ? Files.readString(reasonFile).trim() : null;
              return new QuarantinedFile(p.getFileName().toString(), Files.size(p),
                  Files.getLastModifiedTime(p).toInstant(), reason);
            } catch (IOException e) {
              return new QuarantinedFile(p.getFileName().toString(), -1, null, null);
            }
          }).sorted((a, b) -> String.valueOf(b.modifiedAt()).compareTo(String.valueOf(a.modifiedAt()))).toList();
    }
  }

  private static void requireAuditor() {
    if (!CurrentUser.get().hasAny("AUDITOR", "SYS_ADMIN", "ENFORCEMENT_OFFICER", "CLERK")) {
      throw DomainException.forbidden("Auditors only");
    }
  }

  private static void requireAdmin() {
    if (!CurrentUser.get().hasAny("SYS_ADMIN", "AUDITOR")) {
      throw DomainException.forbidden("Admins only");
    }
  }
}
