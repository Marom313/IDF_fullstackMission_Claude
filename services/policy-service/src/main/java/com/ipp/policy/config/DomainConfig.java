package com.ipp.policy.config;

import com.ipp.policy.application.PolicyCatalogService;
import com.ipp.policy.application.PolicyEventPublisher;
import com.ipp.policy.application.PolicyRepository;
import com.ipp.policy.domain.dsl.PolicyParser;
import com.ipp.policy.domain.dsl.PolicyValidator;
import com.ipp.policy.domain.eval.Evaluator;
import com.ipp.policy.domain.eval.FunctionRegistry;
import com.ipp.policy.domain.eval.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.ZoneId;

/** Wires the framework-free domain and application classes into Spring. */
@Configuration
public class DomainConfig {

    private static final Logger log = LoggerFactory.getLogger(DomainConfig.class);

    @Bean
    Clock clock() { return Clock.system(ZoneId.of("Asia/Jerusalem")); }

    @Bean
    FunctionRegistry functionRegistry() { return FunctionRegistry.standard(); }

    @Bean
    PolicyParser policyParser() { return new PolicyParser(); }

    @Bean
    PolicyValidator policyValidator(FunctionRegistry functions) { return new PolicyValidator(functions); }

    @Bean
    Evaluator evaluator(FunctionRegistry functions) { return new Evaluator(functions); }

    @Bean
    Simulator simulator(Evaluator evaluator) { return new Simulator(evaluator); }

    /** Default event adapter: log only. Replace with the shared outbox adapter during integration. */
    @Bean
    @ConditionalOnMissingBean(PolicyEventPublisher.class)
    PolicyEventPublisher loggingPolicyEventPublisher() {
        return (activated, retired) -> log.info("policy.policy.published.v1 name={} version={} sha256={} retiredVersion={}",
                activated.name(), activated.version(), activated.sha256(), retired == null ? null : retired.version());
    }

    @Bean
    PolicyCatalogService policyCatalogService(PolicyRepository repository, PolicyEventPublisher events, PolicyParser parser,
                                              PolicyValidator validator, Evaluator evaluator, Simulator simulator, Clock clock) {
        return new PolicyCatalogService(repository, events, parser, validator, evaluator, simulator, clock);
    }

    /** Other instances may publish; refresh the in-memory snapshot of active policies periodically. */
    @Configuration
    static class SnapshotRefresher {
        private final PolicyCatalogService service;

        SnapshotRefresher(PolicyCatalogService service) { this.service = service; }

        @Scheduled(fixedDelayString = "${ipp.policy.snapshot-refresh:PT30S}", initialDelayString = "PT30S")
        void refresh() { service.refreshActive(); }
    }
}
