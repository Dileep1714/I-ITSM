package com.iitsm.backend.common.exception;

/**
 * Wrong email or password.
 *
 * <p>The message is deliberately vague: telling an attacker <em>which</em> part was wrong would let them
 * discover which email addresses have accounts.
 */
public class InvalidCredentialsException extends ItsmException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }

    @Override
    public int httpStatus() {
        return 401;
    }
}
