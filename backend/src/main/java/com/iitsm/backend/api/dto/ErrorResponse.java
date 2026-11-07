package com.iitsm.backend.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * The single JSON shape for every API error:
 * <pre>{"code":"INVALID_STATE_TRANSITION","message":"…","status":409,"path":"/api/incidents/7/close",
 *  "timestamp":"…","fieldErrors":[]}</pre>
 * {@code code} is stable and machine-readable (the frontend switches on it); {@code message} is for humans.
 */
public record ErrorResponse(String code, String message, int status, String path, Instant timestamp,
                            List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
