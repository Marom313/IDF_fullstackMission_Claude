package il.ipp.permits.application;

import il.ipp.permits.domain.PermitPorts.PermitRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Moves ACTIVE permits past their valid_to date to EXPIRED (emits status-changed events). */
@Component
class ExpiryJob {
  private final PermitRepository permits;
  private final PermitService service;

  ExpiryJob(PermitRepository permits, PermitService service) {
    this.permits = permits;
    this.service = service;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  void expire() {
    for (UUID id : permits.activeExpiredBefore(LocalDate.now(PermitService.IL), 500)) {
      service.transition(id, "EXPIRE", "system");
    }
  }
}
