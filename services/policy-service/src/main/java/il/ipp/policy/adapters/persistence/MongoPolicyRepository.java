package il.ipp.policy.adapters.persistence;

import il.ipp.policy.application.PolicyErrors;
import il.ipp.policy.application.PolicyRecord;
import il.ipp.policy.application.PolicyRepository;
import il.ipp.policy.application.PolicyStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MongoPolicyRepository implements PolicyRepository {

    private final MongoTemplate mongo;

    MongoPolicyRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public PolicyRecord insert(PolicyRecord record) {
        try {
            return mongo.insert(PolicyDocument.from(record, null)).toRecord();
        } catch (DuplicateKeyException e) {
            throw new PolicyErrors.Conflict("Version " + record.version() + " of '" + record.name() + "' already exists");
        }
    }

    @Override
    public PolicyRecord update(PolicyRecord record) {
        PolicyDocument current = mongo.findById(record.id(), PolicyDocument.class);
        if (current == null) throw new PolicyErrors.NotFound(record.name(), record.version());
        // carries the stored revision → OptimisticLockingFailureException if someone changed it in between
        return mongo.save(PolicyDocument.from(record, current.revision)).toRecord();
    }

    @Override
    public Optional<PolicyRecord> find(String name, int version) {
        Query q = Query.query(Criteria.where("name").is(name).and("version").is(version));
        return Optional.ofNullable(mongo.findOne(q, PolicyDocument.class)).map(PolicyDocument::toRecord);
    }

    @Override
    public List<PolicyRecord> findVersions(String name) {
        Query q = Query.query(Criteria.where("name").is(name)).with(Sort.by(Sort.Direction.DESC, "version"));
        return mongo.find(q, PolicyDocument.class).stream().map(PolicyDocument::toRecord).toList();
    }

    @Override
    public List<PolicyRecord> findByStatus(PolicyStatus status) {
        Query q = Query.query(Criteria.where("status").is(status)).with(Sort.by("name"));
        return mongo.find(q, PolicyDocument.class).stream().map(PolicyDocument::toRecord).toList();
    }

    @Override
    public List<PolicyRecord> findLatestPerName() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "version")),
                Aggregation.group("name").first("$$ROOT").as("doc"),
                Aggregation.replaceRoot("doc"),
                Aggregation.sort(Sort.by("name")));
        return mongo.aggregate(agg, "policies", PolicyDocument.class).getMappedResults().stream()
                .map(PolicyDocument::toRecord).toList();
    }
}
