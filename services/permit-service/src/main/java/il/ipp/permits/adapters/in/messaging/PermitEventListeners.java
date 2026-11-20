package il.ipp.permits.adapters.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.events.EventEnvelope;
import il.ipp.common.events.Topics;
import il.ipp.common.inbox.InboxGuard;
import il.ipp.permits.application.PermitService;
import il.ipp.permits.domain.PermitPorts.PermitRepository;
import il.ipp.permits.domain.PermitPorts.PersonRef;
import il.ipp.permits.domain.PermitPorts.PersonRefRepository;
import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Kafka consumers. Each handler is idempotent (inbox) and transactional. */
@Component
class PermitEventListeners {
  private final ObjectMapper json;
  private final InboxGuard inbox;
  private final PersonRefRepository persons;
  private final PermitRepository permits;
  private final PermitService service;

  PermitEventListeners(ObjectMapper json, InboxGuard inbox, PersonRefRepository persons, PermitRepository permits,
                       PermitService service) {
    this.json = json;
    this.inbox = inbox;
    this.persons = persons;
    this.permits = permits;
    this.service = service;
  }

  /** Read model of persons (One Source of Truth = registry; this is a copy fed by events). */
  @KafkaListener(topics = Topics.PERSON_UPSERTED, groupId = "permit-service.person-ref")
  @Transactional
  public void onPerson(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "permit.person-ref")) {
      return;
    }
    persons.upsert(new PersonRef(UUID.fromString(e.str("personId")), e.str("nationalId"), e.str("fullName"),
        e.str("birthDate") == null ? null : LocalDate.parse(e.str("birthDate")), e.str("securityStatus"),
        e.str("region"), e.str("employerId") == null ? null : UUID.fromString(e.str("employerId"))));
  }

  /** Closing the loop: a sanction suspends the person's active permits; lifting it reinstates them. */
  @KafkaListener(topics = {Topics.SANCTION_IMPOSED, Topics.SANCTION_LIFTED}, groupId = "permit-service.sanctions")
  @Transactional
  public void onSanction(String message) throws Exception {
    EventEnvelope e = json.readValue(message, EventEnvelope.class);
    if (!inbox.firstTime(e.eventId(), "permit.sanctions")) {
      return;
    }
    boolean imposed = "SanctionImposed".equals(e.type());
    for (Permit p : permits.byPerson(UUID.fromString(e.personId()))) {
      if (imposed && p.status() == PermitStatus.ACTIVE) {
        service.transition(p.id(), "SUSPEND", "enforcement-service");
      } else if (!imposed && p.status() == PermitStatus.SUSPENDED) {
        service.transition(p.id(), "REINSTATE", "enforcement-service");
      }
    }
  }
}
