package com.iitsm.backend.common;

import com.iitsm.backend.common.exception.InvalidInputException;

/**
 * Small guard helpers used by domain constructors and methods, so every object is valid from the moment it is
 * created and can never be put into an invalid state.
 *
 * <p>{@code final} with a private constructor: this is a utility class and must not be instantiated or extended.
 */
public final class Validate {

    private Validate() {
    }

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidInputException(field + " is required");
        }
        return value;
    }

    /** Non-blank text, trimmed, no longer than {@code maxLength}. Returns the trimmed value. */
    public static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field + " must not be blank");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new InvalidInputException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    /** Optional text: {@code null} or blank becomes {@code null}; otherwise trimmed and length-checked. */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    /** Integer within the inclusive range {@code [min, max]}. */
    public static int requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new InvalidInputException(field + " must be between " + min + " and " + max + " (was " + value + ")");
        }
        return value;
    }
}
