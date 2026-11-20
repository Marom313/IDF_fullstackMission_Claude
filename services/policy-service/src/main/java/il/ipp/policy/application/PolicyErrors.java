package il.ipp.policy.application;

import il.ipp.policy.domain.dsl.SyntaxError;

import java.util.List;

/** Business exceptions, mapped to RFC 7807 Problem Details by the web adapter. */
public final class PolicyErrors {

    private PolicyErrors() { }

    public static final class InvalidPolicy extends RuntimeException {
        private final List<SyntaxError> errors;

        public InvalidPolicy(List<SyntaxError> errors) {
            super("Policy source is invalid");
            this.errors = List.copyOf(errors);
        }

        public List<SyntaxError> errors() { return errors; }
    }

    public static final class NotFound extends RuntimeException {
        public NotFound(String name, Integer version) {
            super(version == null ? "Policy '" + name + "' not found" : "Policy '" + name + "' v" + version + " not found");
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }
}
