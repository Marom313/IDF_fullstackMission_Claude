package il.ipp.audit.adapters.out.mongo;

import il.ipp.audit.domain.AuditPorts.AuditStore;
import il.ipp.audit.domain.AuditPorts.DeadLetter;
import il.ipp.audit.domain.AuditPorts.DeadLetterStore;
import il.ipp.audit.domain.AuditRecord;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
class MongoAuditStore implements AuditStore {
  private final MongoTemplate mongo;

  MongoAuditStore(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  /** Indexes defined in code (idempotent on every start). */
  @PostConstruct
  void indexes() {
    var ops = mongo.indexOps("audit");
    ops.ensureIndex(new Index().on("entityId", Sort.Direction.ASC).on("at", Sort.Direction.DESC));
    ops.ensureIndex(new Index().on("personId", Sort.Direction.ASC).on("at", Sort.Direction.DESC));
    ops.ensureIndex(new Index().on("at", Sort.Direction.DESC));
    ops.ensureIndex(new Index().on("actor", Sort.Direction.ASC).on("at", Sort.Direction.DESC));
  }

  @Override
  public void append(AuditRecord r) {
    try {
      mongo.insert(toDoc(r), "audit");
    } catch (DuplicateKeyException redelivered) {
      // same eventId already stored → idempotent
    }
  }

  @Override
  public List<AuditRecord> search(String entityId, String personId, String actor, String action, Instant before,
                                  int limit) {
    Criteria c = new Criteria();
    if (entityId != null) {
      c = c.and("entityId").is(entityId);
    }
    if (personId != null) {
      c = c.and("personId").is(personId);
    }
    if (actor != null) {
      c = c.and("actor").is(actor);
    }
    if (action != null) {
      c = c.and("action").is(action);
    }
    if (before != null) {
      c = c.and("at").lt(before);
    }
    Query q = new Query(c).with(Sort.by(Sort.Direction.DESC, "at")).limit(limit);
    return mongo.find(q, Document.class, "audit").stream().map(MongoAuditStore::fromDoc).toList();
  }

  @Override
  public Map<String, Long> countByAction() {
    var agg = Aggregation.newAggregation(Aggregation.group("action").count().as("n"),
        Aggregation.sort(Sort.Direction.DESC, "n"), Aggregation.limit(30));
    Map<String, Long> m = new LinkedHashMap<>();
    mongo.aggregate(agg, "audit", Document.class).forEach(d -> m.put(String.valueOf(d.get("_id")),
        ((Number) d.get("n")).longValue()));
    return m;
  }

  private static Document toDoc(AuditRecord r) {
    return new Document("_id", r.id()).append("at", java.util.Date.from(r.at())).append("service", r.service())
        .append("action", r.action()).append("entityType", r.entityType()).append("entityId", r.entityId())
        .append("personId", r.personId()).append("actor", r.actor()).append("decision", r.decision())
        .append("reasons", r.reasons()).append("policyVersion", r.policyVersion())
        .append("correlationId", r.correlationId());
  }

  @SuppressWarnings("unchecked")
  private static AuditRecord fromDoc(Document d) {
    return new AuditRecord(d.getString("_id"), d.getDate("at").toInstant(), d.getString("service"),
        d.getString("action"), d.getString("entityType"), d.getString("entityId"), d.getString("personId"),
        d.getString("actor"), d.getString("decision"), (List<String>) d.get("reasons"), d.getString("policyVersion"),
        d.getString("correlationId"));
  }
}

@Component
class MongoDeadLetterStore implements DeadLetterStore {
  private final MongoTemplate mongo;

  MongoDeadLetterStore(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public void save(DeadLetter d) {
    try {
      mongo.insert(d, "dlt");
    } catch (DuplicateKeyException ignored) {
      // already captured
    }
  }

  @Override
  public List<DeadLetter> recent(int limit) {
    return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "at")).limit(limit), DeadLetter.class, "dlt");
  }

  @Override
  public Optional<DeadLetter> find(String id) {
    return Optional.ofNullable(mongo.findById(id, DeadLetter.class, "dlt"));
  }

  @Override
  public void markReplayed(String id) {
    mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("replayed", true), "dlt");
  }
}
