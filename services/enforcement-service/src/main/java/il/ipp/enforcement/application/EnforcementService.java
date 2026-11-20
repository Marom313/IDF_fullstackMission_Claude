package il.ipp.enforcement.application;

import il.ipp.common.events.Topics;
import il.ipp.common.outbox.OutboxWriter;
import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.enforcement.domain.Enforcement;
import il.ipp.enforcement.domain.Enforcement.CaseStatus;
import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.ProposedSanction;
import il.ipp.enforcement.domain.Enforcement.Violation;
import il.ipp.enforcement.domain.EnforcementPorts.CaseRecord;
import il.ipp.enforcement.domain.EnforcementPorts.CaseRepository;
import il.ipp.enforcement.domain.EnforcementPorts.CrossingRepository;
import il.ipp.enforcement.domain.EnforcementPorts.PermitSnapshots;
import il.ipp.enforcement.domain.EnforcementPorts.Stats;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closing the loop: crossing → violation → case → sanction → (appeal) → close. Every step is audited. */
@Service
public class EnforcementService {
  public static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
  private final CaseRepository cases;
  private final CrossingRepository crossings;
  private final PermitSnapshots permits;
  private final OutboxWriter outbox;
  private final boolean autoSanction;

  public EnforcementService(CaseRepository cases, CrossingRepository crossings, PermitSnapshots permits,
                            OutboxWriter outbox, @Value("${ipp.enforcement.auto-sanction:true}") boolean autoSanction) {
    this.cases = cases;
    this.crossings = crossings;
    this.permits = permits;
    this.outbox = outbox;
    this.autoSanction = autoSanction;
  }

  @Transactional
  public void recordCrossing(Crossing c) {
    crossings.insert(c);
    crossings.updatePresence(c.personId(), c.nationalId(), c.direction() == Enforcement.Direction.ENTRY,
        c.occurredAt(), c.gate());
    Enforcement.onEntry(c, permits.of(c.personId()), LocalDate.ofInstant(c.occurredAt(), IL))
        .ifPresent(v -> openCase(c.personId(), c.nationalId(), v, "detector:on-entry"));
  }

  /** Manual crossing record from the officer terminal → published to Kafka like a NiFi-ingested one. */
  @Transactional
  public void publishManualCrossing(UUID personId, String nationalId, String gate, String direction) {
    Map<String, Object> p = new HashMap<>();
    p.put("crossingId", UUID.randomUUID().toString());
    p.put("personId", personId.toString());
    p.put("nationalId", nationalId);
    p.put("gate", gate);
    p.put("direction", direction);
    p.put("occurredAt", Instant.now().toString());
    p.put("source", "TERMINAL:" + CurrentUser.get().username());
    outbox.write(Topics.CROSSING_RECORDED, "CrossingRecorded", personId.toString(), p);
  }

  @Transactional
  public Optional<CaseRecord> siteCheck(UUID personId, String nationalId, String observedRegion) {
    return Enforcement.workRegion(observedRegion, permits.of(personId), LocalDate.now(IL))
        .flatMap(v -> openCase(personId, nationalId, v, CurrentUser.get().username()));
  }

  @Transactional
  public Optional<CaseRecord> openCase(UUID personId, String nationalId, Violation v, String actor) {
    if (cases.hasOpenCase(personId, v.type().name())) {
      return Optional.empty();
    }
    ProposedSanction s = Enforcement.proposeFor(v.type());
    Instant now = Instant.now();
    CaseRecord c = new CaseRecord(UUID.randomUUID(), personId, nationalId, v.type().name(), v.details(),
        CaseStatus.OPEN.name(), s.type().name(), s.days(), null, null, null, null, now, now, actor, 0);
    cases.insert(c);
    audit(c, "CASE_OPENED", actor);
    if (autoSanction && s.type() == Enforcement.SanctionType.ENTRY_BAN) {
      return Optional.of(sanction(c, "auto:" + actor));
    }
    return Optional.of(c);
  }

  @Transactional
  public CaseRecord impose(UUID caseId, long version) {
    requireOfficer();
    return sanction(load(caseId, version), CurrentUser.get().username());
  }

  @Transactional
  public CaseRecord dismiss(UUID caseId, long version, String note) {
    requireOfficer();
    CaseRecord c = load(caseId, version);
    return save(c, move(c, CaseStatus.DISMISSED), c.sanctionId(), c.sanctionUntil(), c.appealText(), note,
        "CASE_DISMISSED");
  }

  @Transactional
  public CaseRecord appeal(UUID caseId, long version, String text) {
    if (!CurrentUser.get().hasAny("ENFORCEMENT_OFFICER", "CLERK", "SYS_ADMIN", "WORKER")) {
      throw DomainException.forbidden("Not allowed");
    }
    if (text == null || text.isBlank()) {
      throw new DomainException("APPEAL_TEXT_REQUIRED", 422, "Appeal text is required");
    }
    CaseRecord c = load(caseId, version);
    return save(c, move(c, CaseStatus.APPEALED), c.sanctionId(), c.sanctionUntil(), text, c.decisionNote(),
        "APPEAL_FILED");
  }

  @Transactional
  public CaseRecord decideAppeal(UUID caseId, long version, boolean uphold, String note) {
    requireOfficer();
    CaseRecord c = load(caseId, version);
    CaseRecord decided = save(c, move(c, uphold ? CaseStatus.UPHELD : CaseStatus.OVERTURNED), c.sanctionId(),
        c.sanctionUntil(), c.appealText(), note, uphold ? "APPEAL_REJECTED" : "APPEAL_ACCEPTED");
    if (!uphold && c.sanctionId() != null) {
      outbox.write(Topics.SANCTION_LIFTED, "SanctionLifted", c.personId().toString(), sanctionPayload(decided));
    }
    return close(decided);
  }

  @Transactional
  public CaseRecord closeCase(UUID caseId, long version) {
    requireOfficer();
    return close(load(caseId, version));
  }

  public CaseRecord get(UUID id) {
    requireStaff();
    return cases.find(id).orElseThrow(() -> DomainException.notFound("Case"));
  }

  public List<CaseRecord> list(String status, String after, int limit) {
    requireStaff();
    return cases.list(status, after, Math.min(Math.max(limit, 1), 200));
  }

  public List<CaseRecord> byPerson(UUID personId) {
    requireStaff();
    return cases.byPerson(personId);
  }

  public List<Crossing> crossingsOf(UUID personId) {
    requireStaff();
    return crossings.byPerson(personId, 50);
  }

  public Stats stats() {
    return new Stats(cases.countByStatus(), crossings.count());
  }

  /** Overstay detector, run periodically for persons who are inside the country. */
  @Transactional
  public int detectOverstays() {
    int opened = 0;
    LocalDate today = LocalDate.now(IL);
    for (UUID pid : crossings.insideSince(Instant.now(), 500)) {
      var v = Enforcement.overstay(true, crossings.lastEntry(pid).orElse(null), permits.of(pid), today);
      if (v.isPresent() && openCase(pid, null, v.get(), "detector:overstay").isPresent()) {
        opened++;
      }
    }
    return opened;
  }

  private CaseRecord sanction(CaseRecord c, String actor) {
    LocalDate until = LocalDate.now(IL).plusDays(c.proposedDays());
    CaseRecord s = save(c, move(c, CaseStatus.SANCTIONED), UUID.randomUUID(), until, c.appealText(), c.decisionNote(),
        "SANCTION_IMPOSED", actor);
    outbox.write(Topics.SANCTION_IMPOSED, "SanctionImposed", c.personId().toString(), sanctionPayload(s));
    return s;
  }

  private CaseRecord close(CaseRecord c) {
    return save(c, move(c, CaseStatus.CLOSED), c.sanctionId(), c.sanctionUntil(), c.appealText(), c.decisionNote(),
        "CASE_CLOSED");
  }

  private static String move(CaseRecord c, CaseStatus target) {
    try {
      return CaseStatus.valueOf(c.status()).to(target).name();
    } catch (IllegalStateException e) {
      throw new DomainException("INVALID_TRANSITION", 409, e.getMessage());
    }
  }

  private CaseRecord save(CaseRecord c, String status, UUID sanctionId, LocalDate until, String appeal, String note,
                          String action) {
    return save(c, status, sanctionId, until, appeal, note, action, CurrentUser.get().username());
  }

  private CaseRecord save(CaseRecord c, String status, UUID sanctionId, LocalDate until, String appeal, String note,
                          String action, String actor) {
    CaseRecord n = new CaseRecord(c.id(), c.personId(), c.nationalId(), c.violationType(), c.details(), status,
        c.proposedSanction(), c.proposedDays(), sanctionId, until, appeal, note, c.openedAt(), Instant.now(), actor,
        c.version());
    if (!cases.update(n)) {
      throw DomainException.conflict("CONCURRENT_MODIFICATION", "Case was changed by someone else");
    }
    CaseRecord saved = new CaseRecord(n.id(), n.personId(), n.nationalId(), n.violationType(), n.details(), n.status(),
        n.proposedSanction(), n.proposedDays(), n.sanctionId(), n.sanctionUntil(), n.appealText(), n.decisionNote(),
        n.openedAt(), n.updatedAt(), n.updatedBy(), n.version() + 1);
    audit(saved, action, actor);
    return saved;
  }

  private CaseRecord load(UUID id, long version) {
    CaseRecord c = cases.find(id).orElseThrow(() -> DomainException.notFound("Case"));
    if (c.version() != version) {
      throw DomainException.conflict("CONCURRENT_MODIFICATION", "Case was changed by someone else");
    }
    return c;
  }

  private static Map<String, Object> sanctionPayload(CaseRecord c) {
    Map<String, Object> m = new HashMap<>();
    m.put("sanctionId", String.valueOf(c.sanctionId()));
    m.put("caseId", c.id().toString());
    m.put("type", c.proposedSanction());
    m.put("until", c.sanctionUntil() == null ? null : c.sanctionUntil().toString());
    m.put("reason", c.violationType());
    return m;
  }

  private void audit(CaseRecord c, String action, String actor) {
    Map<String, Object> a = new HashMap<>();
    a.put("service", "enforcement-service");
    a.put("action", action);
    a.put("entityType", "CASE");
    a.put("entityId", c.id().toString());
    a.put("actor", actor);
    a.put("decision", c.status());
    a.put("reasons", List.of(c.violationType(), String.valueOf(c.details())));
    a.put("policyVersion", "sanctions-v1");
    outbox.write(Topics.AUDIT_DECISION, "AuditRecord", c.personId().toString(), a);
  }

  private static void requireOfficer() {
    if (!CurrentUser.get().hasAny("ENFORCEMENT_OFFICER", "SYS_ADMIN")) {
      throw DomainException.forbidden("Enforcement officers only");
    }
  }

  private static void requireStaff() {
    if (!CurrentUser.get().hasAny("ENFORCEMENT_OFFICER", "SYS_ADMIN", "AUDITOR", "CLERK", "CROSSING_OFFICER")) {
      throw DomainException.forbidden("Staff only");
    }
  }
}
