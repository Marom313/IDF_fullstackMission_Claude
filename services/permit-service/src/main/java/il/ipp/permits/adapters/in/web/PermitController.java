package il.ipp.permits.adapters.in.web;

import il.ipp.common.security.CurrentUser;
import il.ipp.permits.application.PermitService;
import il.ipp.permits.application.PermitService.QrBundle;
import il.ipp.permits.application.PermitService.SubmitCommand;
import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitPorts.SectorQuota;
import il.ipp.permits.domain.PermitStatus;
import il.ipp.permits.domain.PermitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permits")
class PermitController {
  private final PermitService service;

  PermitController(PermitService service) {
    this.service = service;
  }

  record PermitDto(UUID id, UUID personId, String nationalId, String type, String status, UUID employerId,
                   UUID licenseId, String region, LocalDate validFrom, LocalDate validTo, String policyVersion,
                   String recommendation, List<String> reasons, Map<String, Object> formData, Instant submittedAt,
                   Instant decidedAt, String decidedBy, long version) {
    static PermitDto of(Permit p) {
      return new PermitDto(p.id(), p.personId(), p.nationalId(), p.type().name(), p.status().name(), p.employerId(),
          p.licenseId(), p.region(), p.validFrom(), p.validTo(), p.policyVersion(), p.recommendation(), p.reasons(),
          p.formData(), p.submittedAt(), p.decidedAt(), p.decidedBy(), p.version());
    }
  }

  record PermitPage(List<PermitDto> items, String nextCursor) {}

  record SubmitRequest(UUID personId, @Pattern(regexp = "\\d{9}") String nationalId, @NotNull PermitType type,
                       String region, Map<String, Object> formData) {}

  record DecisionRequest(@NotNull Boolean approve, List<String> reasons, Integer validityDays, long version) {}

  @GetMapping("/required-fields")
  Map<String, Object> requiredFields(@RequestParam PermitType type) {
    return Map.of("type", type, "requiredFields", service.requiredFields(type));
  }

  @PostMapping("/applications")
  ResponseEntity<PermitDto> submit(@Valid @RequestBody SubmitRequest req,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
    Permit p = service.submit(new SubmitCommand(req.personId(), req.nationalId(), req.type(), req.region(),
        req.formData()), idemKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(PermitDto.of(p));
  }

  @PostMapping("/applications/{id}/decision")
  PermitDto decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
    return PermitDto.of(service.decide(id, req.approve(), req.reasons(), req.validityDays(), req.version()));
  }

  @GetMapping("/applications/queue")
  PermitPage queue(@RequestParam(required = false) String after, @RequestParam(defaultValue = "50") int limit) {
    List<PermitDto> items = service.queue(after, limit).stream().map(PermitDto::of).toList();
    String next = items.size() == limit && !items.isEmpty()
        ? items.get(items.size() - 1).submittedAt().toEpochMilli() + "_" + items.get(items.size() - 1).id() : null;
    return new PermitPage(items, next);
  }

  @GetMapping("/mine")
  List<PermitDto> mine() {
    return service.mine().stream().map(PermitDto::of).toList();
  }

  @GetMapping("/by-person/{personId}")
  List<PermitDto> byPerson(@PathVariable UUID personId) {
    return service.byPerson(personId).stream().map(PermitDto::of).toList();
  }

  @GetMapping("/employer")
  PermitPage employer(@RequestParam(required = false) PermitStatus status,
                      @RequestParam(required = false) String after, @RequestParam(defaultValue = "50") int limit) {
    UUID employerId = service.employerIdOf(CurrentUser.get());
    List<PermitDto> items = service.employerPermits(employerId, status, after, limit).stream()
        .map(PermitDto::of).toList();
    return new PermitPage(items, items.size() == limit ? items.get(items.size() - 1).id().toString() : null);
  }

  @GetMapping("/{id}")
  PermitDto get(@PathVariable UUID id) {
    return PermitDto.of(service.get(id));
  }

  @GetMapping("/{id}/qr")
  QrBundle qr(@PathVariable UUID id) {
    return service.qrFor(id);
  }

  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"permit-" + id + "-SPECIMEN.pdf\"")
        .contentType(MediaType.APPLICATION_PDF).body(service.pdf(id));
  }

  record VerifyRequest(@NotNull String qr) {}

  @PostMapping("/qr/verify")
  Map<String, Object> verify(@Valid @RequestBody VerifyRequest req) {
    String payload = service.verifyQr(req.qr());
    return payload == null ? Map.of("valid", false) : Map.of("valid", true, "payload", payload);
  }

  @GetMapping("/keys/qr")
  Map<String, String> publicKey() {
    return Map.of("alg", "Ed25519", "format", "spki-base64", "key", service.publicKey());
  }

  @GetMapping("/quotas")
  List<SectorQuota> quotas() {
    return service.quotas();
  }

  @GetMapping("/stats")
  Map<String, Long> stats() {
    return service.stats();
  }
}
