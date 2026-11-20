package com.ipp.policy.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A stored policy version. One record per (name, version); versions are immutable once ACTIVE or RETIRED.
 *
 * @param source          the text exactly as the author typed it
 * @param canonicalSource normalised text from {@link com.ipp.policy.domain.dsl.AstPrinter}, used for diffs
 * @param ast             encoded AST ({@link com.ipp.policy.domain.dsl.AstCodec}); what the engine actually runs
 * @param sha256          hash of {@code canonicalSource}; lets audit prove which exact rule produced a decision
 */
public record PolicyRecord(
        String id,
        String name,
        int version,
        PolicyStatus status,
        String source,
        String canonicalSource,
        Map<String, Object> ast,
        String sha256,
        LocalDate effectiveFrom,
        List<String> requiredFields,
        String createdBy,
        Instant createdAt,
        String publishedBy,
        Instant publishedAt) {

    public PolicyRecord withStatus(PolicyStatus newStatus, String by, Instant at) {
        return new PolicyRecord(id, name, version, newStatus, source, canonicalSource, ast, sha256, effectiveFrom,
                requiredFields, createdBy, createdAt,
                newStatus == PolicyStatus.ACTIVE ? by : publishedBy,
                newStatus == PolicyStatus.ACTIVE ? at : publishedAt);
    }
}
