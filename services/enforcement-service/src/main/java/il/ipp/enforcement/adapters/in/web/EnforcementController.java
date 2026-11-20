package il.ipp.enforcement.adapters.in.web;

import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.enforcement.application.EnforcementService;
import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.EnforcementPorts.CaseRecord;
import il.ipp.enforcement.domain.EnforcementPorts.Stats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enforcement")
class EnforcementController {
  private final EnforcementService service;

  EnforcementController(EnforcementService service) {
    this.service = service;
  }

  record CasePage(List<CaseRecord> items, String nextCursor) {}

  record VersionedNote(long version, String note) {}

  record AppealRequest(long version, @NotBlank String text) {}

  record AppealDecision(long version, @NotNull Boolean uphold, String note) {}

  record CrossingRequest(@NotNull UUID personId, @Pattern(regexp = "\\d{9}") String nationalId, @NotBlank String gate,
                         @Pattern(regexp = "ENTRY|EXIT") String direction) {}

  record SiteCheckRequest(@NotNull UUID personId, String nationalId, @NotBlank String region) {}

  @GetMapping("/cases")
  CasePage cases(@RequestParam(required = false) String status, @RequestParam(required = false) String after,
                 @RequestParam(defaultValue = "50") int limit) {
    List<CaseRecord> items = service.list(status, after, limit);
    String next = items.size() == limit && !items.isEmpty()
        ? items.get(items.size() - 1).openedAt().toEpochMilli() + "_" + items.get(items.size() - 1).id() : null;
    return new CasePage(items, next);
  }

  @GetMapping("/cases/{id}")
  CaseRecord get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/persons/{personId}/cases")
  List<CaseRecord> byPerson(@PathVariable UUID personId) {
    return service.byPerson(personId);
  }

  @GetMapping("/persons/{personId}/crossings")
  List<Crossing> crossings(@PathVariable UUID personId) {
    return service.crossingsOf(personId);
  }

  @PostMapping("/cases/{id}/sanction")
  CaseRecord impose(@PathVariable UUID id, @RequestBody VersionedNote req) {
    return service.impose(id, req.version());
  }

  @PostMapping("/cases/{id}/dismiss")
  CaseRecord dismiss(@PathVariable UUID id, @RequestBody VersionedNote req) {
    return service.dismiss(id, req.version(), req.note());
  }

  @PostMapping("/cases/{id}/appeal")
  CaseRecord appeal(@PathVariable UUID id, @Valid @RequestBody AppealRequest req) {
    return service.appeal(id, req.version(), req.text());
  }

  @PostMapping("/cases/{id}/appeal-decision")
  CaseRecord decideAppeal(@PathVariable UUID id, @Valid @RequestBody AppealDecision req) {
    return service.decideAppeal(id, req.version(), req.uphold(), req.note());
  }

  @PostMapping("/cases/{id}/close")
  CaseRecord close(@PathVariable UUID id, @RequestBody VersionedNote req) {
    return service.closeCase(id, req.version());
  }

  @PostMapping("/crossings")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void recordCrossing(@Valid @RequestBody CrossingRequest req) {
    if (!CurrentUser.get().hasAny("CROSSING_OFFICER", "SYS_ADMIN")) {
      throw DomainException.forbidden("Crossing officers only");
    }
    service.publishManualCrossing(req.personId(), req.nationalId(), req.gate(), req.direction());
  }

  @PostMapping("/site-checks")
  CaseRecord siteCheck(@Valid @RequestBody SiteCheckRequest req) {
    if (!CurrentUser.get().hasAny("ENFORCEMENT_OFFICER", "SYS_ADMIN")) {
      throw DomainException.forbidden("Enforcement officers only");
    }
    return service.siteCheck(req.personId(), req.nationalId(), req.region()).orElse(null);
  }

  @PostMapping("/detect-overstays")
  java.util.Map<String, Integer> detect() {
    if (!CurrentUser.get().hasAny("ENFORCEMENT_OFFICER", "SYS_ADMIN")) {
      throw DomainException.forbidden("Enforcement officers only");
    }
    return java.util.Map.of("opened", service.detectOverstays());
  }

  @GetMapping("/stats")
  Stats stats() {
    return service.stats();
  }
}
