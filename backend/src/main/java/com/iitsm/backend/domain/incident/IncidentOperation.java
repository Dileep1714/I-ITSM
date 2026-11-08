package com.iitsm.backend.domain.incident;

/**
 * Lifecycle operations. Each state declares which of these it allows; the API exposes that list so the frontend
 * shows only the buttons that are valid right now. One rule, used by both backend and frontend.
 */
public enum IncidentOperation {
    CLASSIFY,
    ASSIGN,
    ACKNOWLEDGE,
    START_WORK,
    REQUEST_INFO,
    PROVIDE_INFO,
    RESOLVE,
    ACCEPT_RESOLUTION,
    REJECT_RESOLUTION,
    AUTO_VERIFY,
    CLOSE,
    CANCEL,
    /** Cancel as a duplicate of another open incident (support staff; allowed until resolution). */
    MARK_DUPLICATE
}
