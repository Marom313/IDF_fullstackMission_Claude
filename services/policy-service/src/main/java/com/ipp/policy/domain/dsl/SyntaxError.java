package com.ipp.policy.domain.dsl;

/** A problem in policy source. {@code column} is 0-based (ANTLR convention); {@code line} is 1-based. */
public record SyntaxError(int line, int column, String message) { }
