package com.ipp.policy.application;

/**
 * Outbound port for domain events ({@code policy.policy.published.v1}).
 * <p>
 * Default adapter only logs. INTEGRATION TODO (VS Code agent): replace with the shared
 * Transactional Outbox from {@code libs/java-common}. Mongo needs a replica set for multi-document
 * transactions, so the outbox document must be written in the same transaction as the status change.
 */
public interface PolicyEventPublisher {

    void policyPublished(PolicyRecord activated, PolicyRecord retiredOrNull);
}
