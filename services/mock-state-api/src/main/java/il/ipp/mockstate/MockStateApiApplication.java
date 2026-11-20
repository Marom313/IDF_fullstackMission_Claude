package il.ipp.mockstate;

import il.ipp.mockstate.domain.Chaos;
import il.ipp.mockstate.domain.MockPopulation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Mock of external government interfaces (population registry, security status, criminal record).
 * GAP G-03: in production each endpoint is replaced by an adapter to the real system; IPP's own
 * services only depend on the HTTP contract (contracts/openapi/mock-state-api.yaml).
 */
@SpringBootApplication
public class MockStateApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockStateApiApplication.class, args);
    }

    @Bean
    Clock clock() { return Clock.system(ZoneId.of("Asia/Jerusalem")); }

    @Bean
    MockPopulation mockPopulation(@Value("${ipp.mock.seed:42}") long seed,
                                  @Value("${ipp.mock.population-size:5000}") int size) {
        return new MockPopulation(seed, size);
    }

    @Bean
    Chaos chaos() { return new Chaos(); }
}
