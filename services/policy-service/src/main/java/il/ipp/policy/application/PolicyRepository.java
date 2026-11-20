package il.ipp.policy.application;

import java.util.List;
import java.util.Optional;

/** Outbound port for policy storage (implemented by the Mongo adapter). */
public interface PolicyRepository {

    PolicyRecord insert(PolicyRecord record);

    PolicyRecord update(PolicyRecord record);

    Optional<PolicyRecord> find(String name, int version);

    /** All versions of a policy, newest first. */
    List<PolicyRecord> findVersions(String name);

    List<PolicyRecord> findByStatus(PolicyStatus status);

    /** Latest version per policy name (for the Studio list screen). */
    List<PolicyRecord> findLatestPerName();
}
