package il.ipp.policy.adapters.persistence;

import il.ipp.policy.application.PolicyRecord;
import il.ipp.policy.application.PolicyStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mongo shape of a policy version. The unique (name, version) index is what makes concurrent
 * "create draft" requests safe: the second insert fails with DuplicateKeyException → 409.
 * {@code @Version} gives optimistic locking on publish/retire.
 */
@Document("policies")
@CompoundIndexes({
        @CompoundIndex(name = "ux_name_version", def = "{'name': 1, 'version': 1}", unique = true),
        @CompoundIndex(name = "ix_status_name", def = "{'status': 1, 'name': 1}")
})
class PolicyDocument {

    @Id
    String id;
    @Version
    Long revision;
    String name;
    int version;
    PolicyStatus status;
    String source;
    String canonicalSource;
    Map<String, Object> ast;
    String sha256;
    LocalDate effectiveFrom;
    List<String> requiredFields;
    String createdBy;
    Instant createdAt;
    String publishedBy;
    Instant publishedAt;

    static PolicyDocument from(PolicyRecord r, Long revision) {
        PolicyDocument d = new PolicyDocument();
        d.id = r.id();
        d.revision = revision;
        d.name = r.name();
        d.version = r.version();
        d.status = r.status();
        d.source = r.source();
        d.canonicalSource = r.canonicalSource();
        d.ast = r.ast();
        d.sha256 = r.sha256();
        d.effectiveFrom = r.effectiveFrom();
        d.requiredFields = r.requiredFields();
        d.createdBy = r.createdBy();
        d.createdAt = r.createdAt();
        d.publishedBy = r.publishedBy();
        d.publishedAt = r.publishedAt();
        return d;
    }

    PolicyRecord toRecord() {
        return new PolicyRecord(id, name, version, status, source, canonicalSource, ast, sha256, effectiveFrom,
                requiredFields == null ? List.of() : requiredFields, createdBy, createdAt, publishedBy, publishedAt);
    }
}
