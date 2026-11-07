package com.iitsm.backend.common.exception;

/**
 * Input data is missing or malformed (blank name, bad email, out-of-range value…).
 *
 * <p>Named {@code InvalidInputException} rather than {@code ValidationException} to avoid clashing with
 * {@code jakarta.validation.ValidationException}.
 */
public class InvalidInputException extends ItsmException {

    public InvalidInputException(String message) {
        super("INVALID_INPUT", message);
    }

    @Override
    public int httpStatus() {
        return 400;
    }
}
