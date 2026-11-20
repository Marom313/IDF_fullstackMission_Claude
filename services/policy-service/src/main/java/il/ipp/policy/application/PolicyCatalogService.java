package il.ipp.policy.application;

import il.ipp.policy.domain.ast.PolicyDefinition;
import il.ipp.policy.domain.dsl.AstCodec;
import il.ipp.policy.domain.dsl.AstPrinter;
import il.ipp.policy.domain.dsl.LineDiff;
import il.ipp.policy.domain.dsl.ParseResult;
import il.ipp.policy.domain.dsl.PolicyParser;
import il.ipp.policy.domain.dsl.PolicyValidator;
import il.ipp.policy.domain.dsl.SyntaxError;
import il.ipp.policy.domain.eval.Decision;
import il.ipp.policy.domain.eval.Evaluator;
import il.ipp.policy.domain.eval.Facts;
import il.ipp.policy.domain.eval.Simulator;
import il.ipp.policy.domain.eval.SyntheticCases;
import il.ipp.policy.domain.eval.Tri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Use cases of the policy context: validate, save draft, publish, diff, evaluate, simulate.
 * <p>
 * Framework-free on purpose (wired in {@code config.DomainConfig}); it is unit-tested with an
 * in-memory repository, without Spring or Mongo.
 * <p>
 * Active policies are held pre-parsed in an in-memory snapshot, so the evaluate hot path never touches Mongo.
 * The snapshot is replaced atomically on publish and refreshed periodically (other instances may publish).
 */
public class PolicyCatalogService {

    public static final int MAX_SIMULATION_CASES = 10_000;

    private final PolicyRepository repository;
    private final PolicyEventPublisher events;
    private final PolicyParser parser;
    private final PolicyValidator validator;
    private final Evaluator evaluator;
    private final Simulator simulator;
    private final AstPrinter printer = new AstPrinter();
    private final AstCodec codec = new AstCodec();
    private final Clock clock;

    private final AtomicReference<List<PolicyDefinition>> activeSnapshot = new AtomicReference<>(List.of());

    public PolicyCatalogService(PolicyRepository repository, PolicyEventPublisher events, PolicyParser parser,
                                PolicyValidator validator, Evaluator evaluator, Simulator simulator, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.parser = parser;
        this.validator = validator;
        this.evaluator = evaluator;
        this.simulator = simulator;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ validate / draft / publish

    public record ValidationReport(boolean valid, List<SyntaxError> errors, String canonicalSource,
                                   Map<String, Object> ast, List<String> requiredFields, Set<String> referencedPaths) { }

    public ValidationReport validate(String source) {
        ParseResult parsed = parser.parse(source);
        if (!parsed.isValid()) {
            return new ValidationReport(false, parsed.errors(), null, null, List.of(), Set.of());
        }
        PolicyDefinition p = parsed.definition();
        List<SyntaxError> semantic = validator.validate(p);
        return new ValidationReport(semantic.isEmpty(), semantic, printer.print(p), codec.encode(p),
                p.requiredFields(), validator.referencedPaths(p));
    }

    public PolicyRecord createDraft(String source, String user) {
        PolicyDefinition p = parseValid(source);
        if (repository.find(p.name(), p.version()).isPresent()) {
            throw new PolicyErrors.Conflict("Version " + p.version() + " of '" + p.name() + "' already exists. Bump VERSION.");
        }
        int latest = repository.findVersions(p.name()).stream().mapToInt(PolicyRecord::version).max().orElse(0);
        if (p.version() <= latest) {
            throw new PolicyErrors.Conflict("VERSION must be greater than " + latest);
        }
        String canonical = printer.print(p);
        PolicyRecord rec = new PolicyRecord(null, p.name(), p.version(), PolicyStatus.DRAFT, source, canonical,
                codec.encode(p), sha256(canonical), p.effectiveFrom(), p.requiredFields(), user, Instant.now(clock),
                null, null);
        return repository.insert(rec);
    }

    /** Activates a draft and retires the previously active version of the same policy. Idempotent. */
    public PolicyRecord publish(String name, int version, String user) {
        PolicyRecord target = repository.find(name, version).orElseThrow(() -> new PolicyErrors.NotFound(name, version));
        if (target.status() == PolicyStatus.ACTIVE) return target;
        if (target.status() == PolicyStatus.RETIRED) {
            throw new PolicyErrors.Conflict("A retired version cannot be re-published; create a new version.");
        }
        Instant now = Instant.now(clock);
        PolicyRecord retired = repository.findVersions(name).stream()
                .filter(r -> r.status() == PolicyStatus.ACTIVE).findFirst()
                .map(r -> repository.update(r.withStatus(PolicyStatus.RETIRED, user, now)))
                .orElse(null);
        PolicyRecord activated = repository.update(target.withStatus(PolicyStatus.ACTIVE, user, now));
        refreshActive();
        events.policyPublished(activated, retired);
        return activated;
    }

    // ------------------------------------------------------------------ queries

    public List<PolicyRecord> listLatest() { return repository.findLatestPerName(); }

    public List<PolicyRecord> versions(String name) {
        List<PolicyRecord> v = repository.findVersions(name);
        if (v.isEmpty()) throw new PolicyErrors.NotFound(name, null);
        return v;
    }

    public PolicyRecord get(String name, int version) {
        return repository.find(name, version).orElseThrow(() -> new PolicyErrors.NotFound(name, version));
    }

    public List<LineDiff.Line> diff(String name, int from, int to) {
        return LineDiff.diff(get(name, from).canonicalSource(), get(name, to).canonicalSource());
    }

    // ------------------------------------------------------------------ evaluate

    public record Evaluation(Decision decision, List<Decision> applicable) { }

    /**
     * Evaluates a case against every ACTIVE policy whose APPLIES TO matches.
     * If several apply, the strictest outcome wins ("deny overrides"):
     * REJECT > INCOMPLETE > REFER_TO_CLERK > APPROVE.
     */
    public Evaluation evaluate(Facts facts) {
        List<Decision> applicable = new ArrayList<>();
        for (PolicyDefinition p : activeSnapshot.get()) {
            if (p.effectiveFrom() != null && p.effectiveFrom().isAfter(today(facts))) continue;
            Decision d = evaluator.evaluate(p, facts);
            if (d.outcome() != Decision.Outcome.NOT_APPLICABLE) applicable.add(d);
        }
        if (applicable.isEmpty()) {
            Decision none = new Decision(Decision.Outcome.REFER_TO_CLERK, null, 0, null, List.of(),
                    List.of("לא נמצאה מדיניות פעילה שחלה על הבקשה"), null);
            return new Evaluation(none, List.of());
        }
        Decision strictest = applicable.stream().max(Comparator.comparingInt(PolicyCatalogService::severity)).orElseThrow();
        return new Evaluation(strictest, applicable);
    }

    private static int severity(Decision d) {
        return switch (d.outcome()) {
            case REJECT -> 4;
            case INCOMPLETE -> 3;
            case REFER_TO_CLERK -> 2;
            case APPROVE -> 1;
            case NOT_APPLICABLE -> 0;
        };
    }

    /**
     * Mandatory fields for a dynamic form: union of REQUIRE FIELDS of every active policy whose scope
     * matches or may match the partial facts (e.g. only permit.type is known yet).
     */
    public List<String> requiredFields(Facts partial) {
        Set<String> fields = new LinkedHashSet<>();
        for (PolicyDefinition p : activeSnapshot.get()) {
            if (evaluator.scope(p, partial) != Tri.FALSE) fields.addAll(p.requiredFields());
        }
        return List.copyOf(fields);
    }

    // ------------------------------------------------------------------ simulate

    /**
     * Simulates {@code candidateSource} (typically an unsaved draft from the Studio editor) over
     * {@code caseCount} deterministic synthetic cases, comparing with the ACTIVE version of the same name.
     */
    public Simulator.Result simulate(String candidateSource, int caseCount, long seed) {
        if (caseCount < 1 || caseCount > MAX_SIMULATION_CASES) {
            throw new IllegalArgumentException("caseCount must be between 1 and " + MAX_SIMULATION_CASES);
        }
        PolicyDefinition candidate = parseValid(candidateSource);
        PolicyDefinition baseline = activeSnapshot.get().stream()
                .filter(p -> p.name().equals(candidate.name())).findFirst().orElse(null);
        List<Facts> cases = SyntheticCases.generate(caseCount, seed, LocalDate.now(clock));
        return simulator.run(candidate, baseline, cases);
    }

    // ------------------------------------------------------------------ snapshot

    /** Reloads ACTIVE policies from storage into the in-memory snapshot. Called on startup, publish and on a timer. */
    public void refreshActive() {
        List<PolicyDefinition> active = repository.findByStatus(PolicyStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(PolicyRecord::name))
                .map(r -> codec.decodePolicy(r.ast()))
                .toList();
        activeSnapshot.set(active);
    }

    public int activeCount() { return activeSnapshot.get().size(); }

    // ------------------------------------------------------------------ helpers

    private PolicyDefinition parseValid(String source) {
        ParseResult parsed = parser.parse(source);
        if (!parsed.isValid()) throw new PolicyErrors.InvalidPolicy(parsed.errors());
        List<SyntaxError> semantic = validator.validate(parsed.definition());
        if (!semantic.isEmpty()) throw new PolicyErrors.InvalidPolicy(semantic);
        return parsed.definition();
    }

    private LocalDate today(Facts facts) {
        Object t = facts.get("today");
        return t == null ? LocalDate.now(clock) : LocalDate.parse(t.toString());
    }

    static String sha256(String text) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
