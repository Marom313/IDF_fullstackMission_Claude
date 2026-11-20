package il.ipp.policy.domain.eval;

import il.ipp.policy.domain.ast.PolicyDefinition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a candidate policy (usually a draft) over a set of cases and, optionally, compares it with the
 * currently active version, answering "how many decisions would change if we publish this?".
 */
public final class Simulator {

    private static final int MAX_SAMPLES = 25;

    private final Evaluator evaluator;

    public Simulator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public record ChangedCase(String caseId, Decision.Outcome before, Decision.Outcome after, List<String> reasonsAfter) { }

    public record Result(
            int total,
            Map<Decision.Outcome, Integer> candidateCounts,
            Map<Decision.Outcome, Integer> baselineCounts,
            int changed,
            List<ChangedCase> changedSamples,
            long elapsedMicros) { }

    public Result run(PolicyDefinition candidate, PolicyDefinition baseline, List<Facts> cases) {
        long start = System.nanoTime();
        Map<Decision.Outcome, Integer> cand = new EnumMap<>(Decision.Outcome.class);
        Map<Decision.Outcome, Integer> base = new EnumMap<>(Decision.Outcome.class);
        List<ChangedCase> samples = new ArrayList<>();
        int changed = 0;
        for (Facts f : cases) {
            Decision after = evaluator.evaluate(candidate, f);
            cand.merge(after.outcome(), 1, Integer::sum);
            if (baseline != null) {
                Decision before = evaluator.evaluate(baseline, f);
                base.merge(before.outcome(), 1, Integer::sum);
                if (before.outcome() != after.outcome()) {
                    changed++;
                    if (samples.size() < MAX_SAMPLES) {
                        Object id = f.get("caseId");
                        samples.add(new ChangedCase(id == null ? "?" : id.toString(),
                                before.outcome(), after.outcome(), after.reasons()));
                    }
                }
            }
        }
        return new Result(cases.size(), cand, base, changed, samples, (System.nanoTime() - start) / 1_000);
    }
}
