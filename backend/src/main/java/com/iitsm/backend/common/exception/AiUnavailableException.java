package com.iitsm.backend.common.exception;

/** The AI copilot could not answer: no API key is configured, or every model is busy or failing. */
public class AiUnavailableException extends ItsmException {

    public AiUnavailableException(String message) {
        super("AI_UNAVAILABLE", message);
    }

    @Override
    public int httpStatus() {
        return 503;
    }
}
