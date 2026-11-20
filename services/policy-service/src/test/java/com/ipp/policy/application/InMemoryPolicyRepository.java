package com.ipp.policy.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Test double for the repository port. */
public class InMemoryPolicyRepository implements PolicyRepository {

    private final Map<String, PolicyRecord> byKey = new ConcurrentHashMap<>();

    private static String key(String name, int version) { return name + "#" + version; }

    @Override
    public PolicyRecord insert(PolicyRecord r) {
        PolicyRecord saved = new PolicyRecord(UUID.randomUUID().toString(), r.name(), r.version(), r.status(), r.source(),
                r.canonicalSource(), r.ast(), r.sha256(), r.effectiveFrom(), r.requiredFields(), r.createdBy(),
                r.createdAt(), r.publishedBy(), r.publishedAt());
        if (byKey.putIfAbsent(key(r.name(), r.version()), saved) != null) throw new IllegalStateException("duplicate");
        return saved;
    }

    @Override
    public PolicyRecord update(PolicyRecord r) {
        byKey.put(key(r.name(), r.version()), r);
        return r;
    }

    @Override
    public Optional<PolicyRecord> find(String name, int version) {
        return Optional.ofNullable(byKey.get(key(name, version)));
    }

    @Override
    public List<PolicyRecord> findVersions(String name) {
        return byKey.values().stream().filter(r -> r.name().equals(name))
                .sorted(Comparator.comparingInt(PolicyRecord::version).reversed()).toList();
    }

    @Override
    public List<PolicyRecord> findByStatus(PolicyStatus status) {
        return byKey.values().stream().filter(r -> r.status() == status).toList();
    }

    @Override
    public List<PolicyRecord> findLatestPerName() {
        return new ArrayList<>(byKey.values().stream().collect(Collectors.toMap(PolicyRecord::name, r -> r,
                (a, b) -> a.version() > b.version() ? a : b)).values());
    }
}
