package com.iitsm.backend.domain.incident;

/** How an incident was resolved. Used in reports and to find reusable resolutions for similar incidents. */
public enum ResolutionCode {
    PERMANENT_FIX,
    WORKAROUND,
    CONFIGURATION_CHANGE,
    HARDWARE_REPLACED,
    USER_EDUCATION,
    THIRD_PARTY_FIX,
    NOT_REPRODUCIBLE
}
