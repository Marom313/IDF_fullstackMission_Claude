package il.ipp.permits.application;

import il.ipp.common.events.Topics;
import il.ipp.common.idempotency.IdempotencyStore;
import il.ipp.common.outbox.OutboxWriter;
import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitPorts.DocumentRenderer;
import il.ipp.permits.domain.PermitPorts.License;
import il.ipp.permits.domain.PermitPorts.LicenseRepository;
import il.ipp.permits.domain.PermitPorts.PermitRepository;
import il.ipp.permits.domain.PermitPorts.PersonRef;
import il.ipp.permits.domain.PermitPorts.PersonRefRepository;
import il.ipp.permits.domain.PermitPorts.PolicyDecision;
import il.ipp.permits.domain.PermitPorts.PolicyEvaluator;
import il.ipp.permits.domain.PermitPorts.QrSigner;
import il.ipp.permits.domain.PermitPorts.SectorQuota;
import il.ipp.permits.domain.PermitPorts.StateChecks;
import il.ipp.permits.domain.PermitRuleViolation;
import il.ipp.permits.domain.PermitStatus;
import il.ipp.permits.domain.PermitType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permit / application use cases. Every state change writes an outbox event + audit event in the same TX. */
@Service
public class PermitService {
  static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
  private static final String[] STAFF = {"CLERK", "CROSSING_OFFICER", "ENFORCEMENT_OFFICER", "AUDITOR", "SYS_ADMIN",
      "POLICY_ADMIN"};

  private final PermitRepository permits;
  private final PersonRefRepository persons;
  private final LicenseRepository licenses;
  private final PolicyEvaluator policy;
  private final StateChecks state;
  private final QrSigner qr;
  private final DocumentRenderer docs;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;

  public PermitService(PermitRepository permits, PersonRefRepository persons, LicenseRepository licenses,
                       PolicyEvaluator policy, StateChecks state, QrSigner qr, DocumentRenderer docs,
                       OutboxWriter outbox, IdempotencyStore idempotency) {
    this.permits = permits;
    this.persons = persons;
    this.licenses = licenses;
    this.policy = policy;
    this.state = state;
    this.qr = qr;
    this.docs = docs;
    this.outbox = outbox;
    this.idempotency = idempotency;
  }

  public record SubmitCommand(UUID personId, String nationalId, PermitType type, String region,
                              Map<String, Object> formData) {}

  public List<String> requiredFields(PermitType type) {
    return policy.requiredFields(type);
  }

  /**
   * Submit an application. WORKER/DRIVER submit for themselves (personId taken from their profile),
   * EMPLOYER submits for a worker it employs, CLERK for anyone. Idempotency-Key makes retries safe.
   */
  @Transactional
  public Permit submit(SubmitCommand cmd, String idempotencyKey) {
    if (idempotencyKey != null) {
      var existing = idempotency.find(idempotencyKey, "permit-submit");
      if (existing.isPresent()) {
        return get(UUID.fromString(existing.get()));
      }
    }
    CurrentUser u = CurrentUser.get();
    PersonRef person = resolveApplicant(cmd, u);
    UUID employerId = person.employerId();
    UUID licenseId = null;
    if (cmd.type() == PermitType.WORK_ENTRY) {
      if (employerId == null) {
        throw new DomainException("NO_EMPLOYER", 422, "Work permit requires an employer");
      }
      licenseId = licenses.byEmployer(employerId).map(License::id)
          .orElseThrow(() -> new DomainException("NO_LICENSE", 422, "Employer has no active employment license"));
    }
    Permit p;
    try {
      p = Permit.submit(person.personId(), person.nationalId(), cmd.type(), employerId, licenseId,
          cmd.region() != null ? cmd.region() : person.region(), cmd.formData(), policy.requiredFields(cmd.type()));
    } catch (PermitRuleViolation e) {
      throw new DomainException(e.code(), 422, e.getMessage());
    }
    PolicyDecision d = policy.evaluate(cmd.type(), facts(person, licenseId));
    p.recordRecommendation(d.decision(), d.reasons(), d.policyVersion());
    Permit saved = permits.save(p);
    if (idempotencyKey != null && !idempotency.remember(idempotencyKey, "permit-submit", saved.id().toString())) {
      throw DomainException.conflict("DUPLICATE_REQUEST", "Concurrent request with the same Idempotency-Key");
    }
    publish(saved, "SUBMITTED", u.username());
    return saved;
  }

  @Transactional
  public Permit decide(UUID id, boolean approve, List<String> reasons, Integer validityDays, long expectedVersion) {
    CurrentUser u = CurrentUser.get();
    if (!u.hasAny("CLERK", "SYS_ADMIN")) {
      throw DomainException.forbidden("Only clerks decide applications");
    }
    Permit p = permits.findById(id).orElseThrow(() -> DomainException.notFound("Permit"));
    if (p.version() != expectedVersion) {
      throw DomainException.conflict("CONCURRENT_MODIFICATION", "Application was changed by someone else");
    }
    try {
      if (approve) {
        PersonRef person = persons.find(p.personId()).orElseThrow(() -> DomainException.notFound("Person"));
        String sec = state.securityStatus(p.nationalId());
        if ("DENIED".equals(sec)) {
          throw new DomainException("SECURITY_DENIED", 422, "Security check denied – cannot approve");
        }
        if (p.type() == PermitType.WORK_ENTRY) {
          String sector = licenses.findById(p.licenseId()).map(License::sector).orElse("SERVICES");
          if (!licenses.consumeQuota(p.licenseId(), sector)) {
            throw new DomainException("QUOTA_EXHAUSTED", 422, "Sector quota exhausted: " + sector);
          }
        }
        PolicyDecision d = policy.evaluate(p.type(), facts(person, p.licenseId()));
        int days = validityDays != null ? validityDays : d.validityDays();
        p.approve(u.username(), days, LocalDate.now(IL), reasons == null || reasons.isEmpty()
            ? List.of("APPROVED_BY_CLERK", "SECURITY:" + sec) : reasons);
      } else {
        p.reject(u.username(), reasons);
      }
    } catch (PermitRuleViolation e) {
      throw new DomainException(e.code(), 409, e.getMessage());
    }
    Permit saved = permits.save(p);
    publish(saved, approve ? "APPROVED" : "REJECTED", u.username());
    return saved;
  }

  public Permit get(UUID id) {
    Permit p = permits.findById(id).orElseThrow(() -> DomainException.notFound("Permit"));
    authorizeRead(p);
    return p;
  }

  public List<Permit> mine() {
    PersonRef me = myPerson();
    return permits.byPerson(me.personId());
  }

  public List<Permit> byPerson(UUID personId) {
    CurrentUser u = CurrentUser.get();
    if (!u.hasAny(STAFF)) {
      throw DomainException.forbidden("Staff only");
    }
    return permits.byPerson(personId);
  }

  public List<Permit> queue(String after, int limit) {
    if (!CurrentUser.get().hasAny("CLERK", "SYS_ADMIN", "AUDITOR")) {
      throw DomainException.forbidden("Clerks only");
    }
    return permits.queue(List.of(PermitStatus.SUBMITTED, PermitStatus.IN_REVIEW), after, Math.min(limit, 200));
  }

  /** ABAC: employer sees only permits under its own employerId (derived from token, not from the request). */
  public List<Permit> employerPermits(UUID employerId, PermitStatus status, String after, int limit) {
    return permits.byEmployer(employerId, status, after, Math.min(limit, 200));
  }

  public List<SectorQuota> quotas() {
    return licenses.quotas();
  }

  public Map<String, Long> stats() {
    return permits.countByStatus();
  }

  public record QrBundle(String qr, String publicKey) {}

  public QrBundle qrFor(UUID permitId) {
    Permit p = get(permitId);
    if (p.status() != PermitStatus.ACTIVE) {
      throw DomainException.conflict("PERMIT_NOT_ACTIVE", "QR is issued only for active permits");
    }
    return new QrBundle(qr.sign(qrPayload(p)), qr.publicKeySpkiBase64());
  }

  public String verifyQr(String code) {
    return qr.verify(code);
  }

  public String publicKey() {
    return qr.publicKeySpkiBase64();
  }

  public byte[] pdf(UUID permitId) {
    Permit p = get(permitId);
    PersonRef person = persons.find(p.personId()).orElseThrow(() -> DomainException.notFound("Person"));
    String code = p.status() == PermitStatus.ACTIVE ? qr.sign(qrPayload(p)) : "NOT-ACTIVE";
    return docs.permitPdf(p, person, code);
  }

  /** Called by the expiry job and by sanction events. */
  @Transactional
  public void transition(UUID id, String action, String actor) {
    Permit p = permits.findById(id).orElse(null);
    if (p == null) {
      return;
    }
    try {
      switch (action) {
        case "EXPIRE" -> p.expire();
        case "SUSPEND" -> p.suspend();
        case "REINSTATE" -> p.reinstate();
        default -> throw new IllegalArgumentException(action);
      }
    } catch (PermitRuleViolation e) {
      return; // already in a state where this does not apply
    }
    publish(permits.save(p), action, actor);
  }

  static String qrPayload(Permit p) {
    return "{\"p\":\"" + p.id() + "\",\"n\":\"" + p.nationalId() + "\",\"t\":\"" + p.type() + "\",\"to\":\""
        + p.validTo() + "\"}";
  }

  private PersonRef resolveApplicant(SubmitCommand cmd, CurrentUser u) {
    if (u.hasAny("CLERK", "SYS_ADMIN")) {
      return (cmd.personId() != null ? persons.find(cmd.personId()) : persons.findByNationalId(cmd.nationalId()))
          .orElseThrow(() -> DomainException.notFound("Person"));
    }
    if (u.has("EMPLOYER")) {
      PersonRef p = (cmd.personId() != null ? persons.find(cmd.personId()) : persons.findByNationalId(cmd.nationalId()))
          .orElseThrow(() -> DomainException.notFound("Person"));
      UUID myEmployer = employerIdOf(u);
      if (p.employerId() == null || !p.employerId().equals(myEmployer)) {
        throw DomainException.notFound("Person");
      }
      return p;
    }
    return myPerson();
  }

  private PersonRef myPerson() {
    String username = CurrentUser.get().username();
    int idx = switch (username) {
      case "worker1" -> 0;
      case "driver1" -> 1;
      default -> -1;
    };
    if (idx < 0) {
      throw DomainException.notFound("Person profile for current user");
    }
    return persons.find(il.ipp.common.seed.SeedCatalog.personId(idx))
        .orElseThrow(() -> DomainException.notFound("Person profile"));
  }

  /** Demo user→employer link (production: claim from IdP / registry lookup). */
  public UUID employerIdOf(CurrentUser u) {
    if ("employer1".equals(u.username())) {
      return il.ipp.common.seed.SeedCatalog.employerId(0);
    }
    throw DomainException.forbidden("No employer linked to user");
  }

  private void authorizeRead(Permit p) {
    CurrentUser u = CurrentUser.get();
    if (u.hasAny(STAFF)) {
      return;
    }
    if (u.has("EMPLOYER") && p.employerId() != null && p.employerId().equals(employerIdOf(u))) {
      return;
    }
    if (u.hasAny("WORKER", "DRIVER") && p.personId().equals(myPerson().personId())) {
      return;
    }
    throw DomainException.notFound("Permit");
  }

  private Map<String, Object> facts(PersonRef person, UUID licenseId) {
    Map<String, Object> f = new HashMap<>();
    f.put("person.age", person.birthDate() == null ? 0
        : java.time.Period.between(person.birthDate(), LocalDate.now(IL)).getYears());
    f.put("person.securityStatus", person.securityStatus());
    f.put("person.region", person.region());
    f.put("person.nationalId", person.nationalId());
    f.put("person.id", person.personId().toString());
    if (licenseId != null) {
      licenses.findById(licenseId).ifPresent(l -> {
        f.put("employer.sector", l.sector());
        f.put("employer.id", l.employerId().toString());
        licenses.quotas().stream().filter(q -> q.sector().equals(l.sector())).findFirst()
            .ifPresent(q -> f.put("quota." + l.sector() + ".remaining", q.remaining()));
      });
    }
    return f;
  }

  private void publish(Permit p, String action, String actor) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("permitId", p.id().toString());
    payload.put("personId", p.personId().toString());
    payload.put("nationalId", p.nationalId());
    payload.put("type", p.type().name());
    payload.put("status", p.status().name());
    payload.put("region", p.region());
    payload.put("validFrom", p.validFrom() == null ? null : p.validFrom().toString());
    payload.put("validTo", p.validTo() == null ? null : p.validTo().toString());
    payload.put("policyVersion", p.policyVersion());
    payload.put("employerId", p.employerId() == null ? null : p.employerId().toString());
    outbox.write(Topics.PERMIT_STATUS_CHANGED, "PermitStatusChanged", p.personId().toString(), payload);

    Map<String, Object> audit = new HashMap<>();
    audit.put("service", "permit-service");
    audit.put("action", "PERMIT_" + action);
    audit.put("entityType", "PERMIT");
    audit.put("entityId", p.id().toString());
    audit.put("actor", actor);
    audit.put("decision", p.status().name());
    audit.put("reasons", p.reasons());
    audit.put("policyVersion", p.policyVersion());
    outbox.write(Topics.AUDIT_DECISION, "AuditRecord", p.personId().toString(), audit);
  }
}
