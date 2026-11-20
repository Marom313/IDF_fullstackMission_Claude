package il.ipp.policy.domain.dsl;

import il.ipp.policy.domain.ast.PolicyDefinition;

import java.util.List;
import java.util.Optional;

public record ParseResult(PolicyDefinition definition, List<SyntaxError> errors) {

    public ParseResult {
        errors = List.copyOf(errors);
    }

    static ParseResult success(PolicyDefinition d) { return new ParseResult(d, List.of()); }

    static ParseResult failure(List<SyntaxError> errors) { return new ParseResult(null, errors); }

    public boolean isValid() { return definition != null && errors.isEmpty(); }

    public Optional<PolicyDefinition> asOptional() { return Optional.ofNullable(isValid() ? definition : null); }
}
