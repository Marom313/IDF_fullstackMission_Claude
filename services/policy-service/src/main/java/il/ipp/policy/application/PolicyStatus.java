package il.ipp.policy.application;

/** Lifecycle: DRAFT → ACTIVE → RETIRED. Only one ACTIVE version per policy name. */
public enum PolicyStatus { DRAFT, ACTIVE, RETIRED }
