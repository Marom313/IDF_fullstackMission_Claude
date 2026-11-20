package com.ipp.policy.config;

import com.ipp.policy.application.PolicyCatalogService;
import com.ipp.policy.application.PolicyErrors;
import com.ipp.policy.application.PolicyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Loads the bundled demo policies (classpath:policies/*.ipp) on startup and publishes them.
 * Idempotent: an existing name+version is skipped, so restarts and multiple replicas are safe.
 */
@Component
@ConditionalOnProperty(name = "ipp.policy.seed-enabled", havingValue = "true", matchIfMissing = true)
class SeedPolicies implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedPolicies.class);

    private final PolicyCatalogService service;

    SeedPolicies(PolicyCatalogService service) { this.service = service; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:policies/*.ipp");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        for (Resource file : files) {
            String source = file.getContentAsString(StandardCharsets.UTF_8);
            try {
                PolicyRecord draft = service.createDraft(source, "system-seed");
                service.publish(draft.name(), draft.version(), "system-seed");
                log.info("Seeded policy {} v{}", draft.name(), draft.version());
            } catch (PolicyErrors.Conflict alreadyThere) {
                log.debug("Seed {} already present", file.getFilename());
            } catch (org.springframework.dao.DuplicateKeyException raceWithOtherReplica) {
                log.debug("Seed {} inserted concurrently by another replica", file.getFilename());
            }
        }
        service.refreshActive();
        log.info("Active policies loaded: {}", service.activeCount());
    }
}
