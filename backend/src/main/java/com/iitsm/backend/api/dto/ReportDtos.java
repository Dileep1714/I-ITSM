package com.iitsm.backend.api.dto;

import com.iitsm.backend.domain.catalog.BusinessCriticality;
import com.iitsm.backend.domain.catalog.ServiceStatus;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.user.Availability;
import com.iitsm.backend.domain.user.SupportLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * The three reports the spec asks for: incident overview, technician workload and service analysis. Durations are
 * in minutes; averages are {@code null} when there is nothing to average.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** The reporting period: whole days in UTC, {@code to} inclusive. */
    public record Period(LocalDate from, LocalDate to) {
    }

    public record Count<K>(K key, long count) {
    }

    // ----- incident overview -------------------------------------------------------------------------------------

    public record OverviewReport(Period period, Totals totals, List<Count<IncidentStatus>> byStatus,
                                 List<PriorityRow> byPriority, List<Count<String>> byCategory,
                                 List<DailyRow> daily) {
    }

    /**
     * @param slaCompliancePercent share of incidents with a final SLA outcome (resolved, or already breached) that
     *                             met both targets
     */
    public record Totals(long created, long openNow, long resolved, long closed, long cancelled, long slaBreached,
                         Double slaCompliancePercent, Double avgResponseMinutes, Double avgResolutionMinutes,
                         Double avgSatisfaction, long feedbackCount) {
    }

    public record PriorityRow(PriorityLevel priority, long count, long open, long breached,
                              Double avgResolutionMinutes) {
    }

    public record DailyRow(LocalDate date, long created, long resolved) {
    }

    // ----- technician workload -----------------------------------------------------------------------------------

    /**
     * @param activeIncidents    current workload (right now, not limited to the period)
     * @param utilizationPercent active ÷ capacity
     */
    public record TechnicianRow(Long technicianId, String fullName, SupportLevel supportLevel,
                                Availability availability, boolean active, int activeIncidents,
                                int maxActiveIncidents, double utilizationPercent, long assignedInPeriod,
                                long resolvedInPeriod, Double avgResolutionMinutes, long slaBreaches,
                                Double avgSatisfaction) {
    }

    public record WorkloadReport(Period period, List<TechnicianRow> technicians) {
    }

    // ----- service analysis --------------------------------------------------------------------------------------

    public record ServiceRow(Long serviceId, String name, BusinessCriticality criticality, ServiceStatus status,
                             long total, long open, long highPriority, long slaBreaches,
                             Double avgResolutionMinutes, String topCategory, List<String> dependsOn) {
    }

    public record ServiceReport(Period period, List<ServiceRow> services) {
    }
}
