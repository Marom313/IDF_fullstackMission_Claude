package com.ipp.policy.adapters.web;

import com.ipp.policy.application.PolicyErrors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Maps business exceptions to RFC 7807 Problem Details with a stable business error code. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(PolicyErrors.InvalidPolicy.class)
    ProblemDetail invalid(PolicyErrors.InvalidPolicy ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_INVALID", "המדיניות אינה תקינה");
        pd.setProperty("errors", ex.errors());
        return pd;
    }

    @ExceptionHandler(PolicyErrors.NotFound.class)
    ProblemDetail notFound(PolicyErrors.NotFound ex) {
        return problem(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PolicyErrors.Conflict.class)
    ProblemDetail conflict(PolicyErrors.Conflict ex) {
        return problem(HttpStatus.CONFLICT, "POLICY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrent(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "POLICY_CONCURRENT_UPDATE", "הגרסה עודכנה במקביל. נסה שוב");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:ipp:error:" + code.toLowerCase()));
        pd.setProperty("code", code);
        return pd;
    }
}
