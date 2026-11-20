package il.ipp.mockstate.domain;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fault injection, so the demo can show resilience live: "the state registry is slow / failing / down,
 * and IPP keeps working (circuit breaker opens, cases go to a clerk instead of being approved)".
 */
public final class Chaos {

    /**
     * @param latencyMs   fixed delay added to every call
     * @param jitterMs    extra random delay 0..jitterMs
     * @param failureRate 0.0–1.0 probability of answering 503
     * @param outage      true = every call fails
     */
    public record Settings(int latencyMs, int jitterMs, double failureRate, boolean outage) {
        public Settings {
            if (latencyMs < 0 || latencyMs > 30_000) throw new IllegalArgumentException("latencyMs must be 0..30000");
            if (jitterMs < 0 || jitterMs > 30_000) throw new IllegalArgumentException("jitterMs must be 0..30000");
            if (failureRate < 0 || failureRate > 1) throw new IllegalArgumentException("failureRate must be 0..1");
        }

        public static Settings healthy() { return new Settings(0, 0, 0, false); }
    }

    public enum Effect { PROCEED, FAIL }

    private final AtomicReference<Settings> settings = new AtomicReference<>(Settings.healthy());
    private final SplittableRandom random = new SplittableRandom();

    public Settings get() { return settings.get(); }

    public void set(Settings s) { settings.set(s); }

    public void reset() { settings.set(Settings.healthy()); }

    /** Delay (ms) to apply before answering. */
    public synchronized long delayMs() {
        Settings s = settings.get();
        return s.latencyMs() + (s.jitterMs() == 0 ? 0 : random.nextInt(s.jitterMs() + 1));
    }

    public synchronized Effect roll() {
        Settings s = settings.get();
        if (s.outage()) return Effect.FAIL;
        return random.nextDouble() < s.failureRate() ? Effect.FAIL : Effect.PROCEED;
    }
}
