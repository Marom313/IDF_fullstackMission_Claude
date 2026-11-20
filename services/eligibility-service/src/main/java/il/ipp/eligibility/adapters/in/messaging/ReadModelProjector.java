package il.ipp.eligibility.adapters.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.events.Topics;
import il.ipp.common.inbox.InboxGuard;
import il.ipp.eligibility.domain.EligibilityPorts.ReadModel;
import il.ipp.eligibility.domain.EligibilityPorts.ViewCache;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Projects events into the read model. Idempotent (inbox) + transactional.
 * Cache eviction happens AFTER commit, so a concurrent check can't re-cache the old state.
 * Group id is shared by all instances (--scale): each event is handled once.
 */
@Component
class ReadModelProjector {
  private final ObjectMapper json;
  private final InboxGuard inbox;
  private final ReadModel readModel;
  private final ViewCache cache;

  ReadModelProjector(ObjectMapper json, InboxGuard inbox, ReadModel readModel, ViewCache cache) {
    this.json = json;
    this.inbox = inbox;
    this.readModel = readModel;
    this.cache = cache;
  }

  @KafkaListener(topics = Topics.PERSON_UPSERTED, groupId = "eligibility-service")
  @Transactional
  public void onPerson(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "eligibility.person")) {
      return;
    }
    UUID pid = UUID.fromString(e.str("personId"));
    readModel.upsertPerson(pid, e.str("nationalId"), e.str("fullName"), e.str("securityStatus"), e.str("region"));
    evictAfterCommit(pid);
  }

  @KafkaListener(topics = Topics.PERMIT_STATUS_CHANGED, groupId = "eligibility-service")
  @Transactional
  public void onPermit(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "eligibility.permit")) {
      return;
    }
    UUID pid = UUID.fromString(e.str("personId"));
    readModel.upsertPermit(UUID.fromString(e.str("permitId")), pid, e.str("type"), e.str("status"),
        date(e.str("validFrom")), date(e.str("validTo")), e.str("region"), e.str("policyVersion"));
    evictAfterCommit(pid);
  }

  @KafkaListener(topics = {Topics.SANCTION_IMPOSED, Topics.SANCTION_LIFTED}, groupId = "eligibility-service")
  @Transactional
  public void onSanction(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "eligibility.sanction")) {
      return;
    }
    UUID pid = UUID.fromString(e.personId());
    readModel.upsertSanction(UUID.fromString(e.str("sanctionId")), pid, e.str("type"), date(e.str("until")),
        e.str("reason"), "SanctionImposed".equals(e.type()));
    evictAfterCommit(pid);
  }

  private void evictAfterCommit(UUID pid) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        cache.evict(pid);
      }
    });
  }

  private static LocalDate date(String s) {
    return s == null || s.isBlank() || "null".equals(s) ? null : LocalDate.parse(s);
  }
}
