package com.iitsm.backend.common.exception;

/**
 * Root of every application-specific exception in I-ITSM.
 *
 * <p>Each subclass declares its own HTTP status through {@link #httpStatus()} (polymorphism), so a single
 * global handler can turn any of them into a consistent JSON error without a chain of {@code instanceof} checks.
 *
 * <p>The HTTP status is a plain {@code int} on purpose: the domain layer should not depend on the web framework.
 */
public abstract class ItsmException extends RuntimeException {

    private final String code;

    protected ItsmException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Stable, machine-readable error code, for example {@code "INVALID_STATE_TRANSITION"}. */
    public String getCode() {
        return code;
    }

    /** HTTP status code the API should return for this exception. */
    public abstract int httpStatus();
}
