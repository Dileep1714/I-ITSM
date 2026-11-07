package com.iitsm.backend.domain.catalog;

/** Current health of an IT service, shown on the service-health dashboard. */
public enum ServiceStatus {
    OPERATIONAL,
    DEGRADED,
    PARTIAL_OUTAGE,
    MAJOR_OUTAGE,
    MAINTENANCE
}
