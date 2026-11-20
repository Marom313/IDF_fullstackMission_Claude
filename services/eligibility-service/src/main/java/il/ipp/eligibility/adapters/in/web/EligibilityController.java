package il.ipp.eligibility.adapters.in.web;

import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.eligibility.application.EligibilityService;
import il.ipp.eligibility.application.EligibilityService.CheckResponse;
import il.ipp.eligibility.domain.Eligibility.Purpose;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eligibility")
class EligibilityController {
  private final EligibilityService service;

  EligibilityController(EligibilityService service) {
    this.service = service;
  }

  /** e.g. GET /api/eligibility/check?nationalId=900000001&amp;purpose=ENTRY */
  @GetMapping("/check")
  CheckResponse check(@RequestParam(required = false) String nationalId, @RequestParam(required = false) UUID personId,
                      @RequestParam(defaultValue = "ENTRY") Purpose purpose,
                      @RequestParam(required = false) String region) {
    CurrentUser u = CurrentUser.get();
    if (!u.hasAny("CROSSING_OFFICER", "CLERK", "EMPLOYER", "ENFORCEMENT_OFFICER", "SYS_ADMIN", "AUDITOR")) {
      throw DomainException.forbidden("Not allowed to run eligibility checks");
    }
    if (nationalId == null && personId == null) {
      throw new DomainException("MISSING_ID", 400, "nationalId or personId is required");
    }
    return service.check(nationalId, personId, purpose, region);
  }

  @GetMapping("/stats")
  Map<String, Object> stats() {
    return Map.of("cacheHitRatio", service.hitRatio());
  }
}
