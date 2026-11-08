package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.ReportDtos.Count;
import com.iitsm.backend.api.dto.ReportDtos.DailyRow;
import com.iitsm.backend.api.dto.ReportDtos.OverviewReport;
import com.iitsm.backend.api.dto.ReportDtos.Period;
import com.iitsm.backend.api.dto.ReportDtos.PriorityRow;
import com.iitsm.backend.api.dto.ReportDtos.ServiceReport;
import com.iitsm.backend.api.dto.ReportDtos.ServiceRow;
import com.iitsm.backend.api.dto.ReportDtos.TechnicianRow;
import com.iitsm.backend.api.dto.ReportDtos.Totals;
import com.iitsm.backend.api.dto.ReportDtos.WorkloadReport;
import com.iitsm.backend.common.exception.AuthorizationException;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaTracker;
import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.SupportLevel;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.ItServiceRepository;
import com.iitsm.backend.repository.TechnicianRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The management reports: incident overview, technician workload and service analysis, for a period of whole days.
 *
 * <p>Incidents created in the period are loaded once and aggregated in Java. For a course-sized dataset (thousands
 * of incidents) this is fast, and it keeps every metric in readable code next to its definition instead of in
 * long SQL. For millions of rows the same metrics would move into database aggregate queries.
 *
 * <p>Support staff only: technicians and administrators.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final IncidentRepository incidents;
    private final TechnicianRepository technicians;
    private final ItServiceRepository services;
    private final AssignmentCandidateService workload;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public ReportService(IncidentRepository incidents, TechnicianRepository technicians,
                         ItServiceRepository services, AssignmentCandidateService workload,
                         CurrentUserService currentUser, Clock clock) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.services = services;
        this.workload = workload;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ----- incident overview -------------------------------------------------------------------------------------

    public OverviewReport overview(LocalDate from, LocalDate to) {
        requireStaff();
        Period period = period(from, to);
        Instant now = clock.instant();
        List<Incident> list = load(period);

        Totals totals = new Totals(
                list.size(),
                count(list, Incident::isOpen),
                count(list, i -> i.getSla().getResolvedAt() != null),
                count(list, i -> i.getStatus() == IncidentStatus.CLOSED),
                count(list, i -> i.getStatus() == IncidentStatus.CANCELLED),
                count(list, i -> breached(i, now)),
                slaCompliance(list, now),
                average(list, ReportService::responseMinutes),
                average(list, ReportService::resolutionMinutes),
                average(list, ReportService::rating),
                count(list, i -> rating(i) != null));

        Map<IncidentStatus, Long> byStatus = list.stream()
                .collect(Collectors.groupingBy(Incident::getStatus, () -> new EnumMap<>(IncidentStatus.class),
                        Collectors.counting()));

        List<PriorityRow> byPriority = new ArrayList<>();
        for (PriorityLevel level : PriorityLevel.values()) {
            List<Incident> atLevel = list.stream()
                    .filter(i -> i.getPriority() != null && i.getPriority().getLevel() == level)
                    .toList();
            byPriority.add(new PriorityRow(level, atLevel.size(), count(atLevel, Incident::isOpen),
                    count(atLevel, i -> breached(i, now)), average(atLevel, ReportService::resolutionMinutes)));
        }

        List<Count<String>> byCategory = list.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getCategory() != null ? i.getCategory().getName() : "Unclassified",
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Count<>(e.getKey(), e.getValue()))
                .toList();

        return new OverviewReport(period, totals,
                byStatus.entrySet().stream().map(e -> new Count<>(e.getKey(), e.getValue())).toList(),
                byPriority, byCategory, daily(period, list));
    }

    /** One row per day of the period, including days with nothing, so charts have a continuous axis. */
    private static List<DailyRow> daily(Period period, List<Incident> list) {
        Map<LocalDate, Long> created = new TreeMap<>(list.stream()
                .collect(Collectors.groupingBy(i -> day(i.getCreatedAt()), Collectors.counting())));
        Map<LocalDate, Long> resolved = new TreeMap<>(list.stream()
                .filter(i -> i.getSla().getResolvedAt() != null)
                .collect(Collectors.groupingBy(i -> day(i.getSla().getResolvedAt()), Collectors.counting())));
        List<DailyRow> rows = new ArrayList<>();
        for (LocalDate d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
            rows.add(new DailyRow(d, created.getOrDefault(d, 0L), resolved.getOrDefault(d, 0L)));
        }
        return rows;
    }

    // ----- technician workload -----------------------------------------------------------------------------------

    public WorkloadReport technicianWorkload(LocalDate from, LocalDate to) {
        User user = requireStaff();
        Period period = period(from, to);
        Instant now = clock.instant();
        List<Incident> list = load(period);
        Map<Long, Long> active = workload.workloadByTechnician();

        List<Technician> people = technicians.findAll(Sort.by("fullName"));
        if (user instanceof Technician self && !self.getSupportLevel().isAtLeast(SupportLevel.L2_TEAM_LEAD)) {
            // Front-line technicians see only their own row; team leads and above see their whole team.
            people = people.stream().filter(self::equals).toList();
        }

        List<TechnicianRow> rows = people.stream().map(t -> {
            List<Incident> assigned = list.stream().filter(i -> t.equals(i.getAssignee())).toList();
            List<Incident> resolvedBy = list.stream()
                    .filter(i -> i.getResolution() != null && t.equals(i.getResolution().getResolvedBy()))
                    .toList();
            int current = active.getOrDefault(t.getId(), 0L).intValue();
            return new TechnicianRow(t.getId(), t.getFullName(), t.getSupportLevel(), t.getAvailability(),
                    t.isActive(), current, t.getMaxActiveIncidents(), round(t.utilization(current) * 100),
                    assigned.size(), resolvedBy.size(), average(resolvedBy, ReportService::resolutionMinutes),
                    count(assigned, i -> breached(i, now)), average(resolvedBy, ReportService::rating));
        }).toList();
        return new WorkloadReport(period, rows);
    }

    // ----- service analysis --------------------------------------------------------------------------------------

    public ServiceReport serviceAnalysis(LocalDate from, LocalDate to) {
        requireStaff();
        Period period = period(from, to);
        Instant now = clock.instant();
        Map<ItService, List<Incident>> byService = load(period).stream()
                .collect(Collectors.groupingBy(Incident::getService));

        List<ServiceRow> rows = services.findAllByOrderByName().stream().map(service -> {
            List<Incident> list = byService.getOrDefault(service, List.of());
            String topCategory = list.stream()
                    .filter(i -> i.getCategory() != null)
                    .collect(Collectors.groupingBy(i -> i.getCategory().getName(), Collectors.counting()))
                    .entrySet().stream()
                    // most incidents; on a tie, the alphabetically first name
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            return new ServiceRow(service.getId(), service.getName(), service.getCriticality(), service.getStatus(),
                    list.size(), count(list, Incident::isOpen),
                    count(list, i -> i.getPriority() != null && i.getPriority().getLevel().compareTo(PriorityLevel.P2) <= 0),
                    count(list, i -> breached(i, now)), average(list, ReportService::resolutionMinutes), topCategory,
                    service.getDependsOn().stream().map(ItService::getName).sorted().toList());
        })
                .sorted(Comparator.comparingLong(ServiceRow::total).reversed().thenComparing(ServiceRow::name))
                .toList();
        return new ServiceReport(period, rows);
    }

    // ----- metric definitions ------------------------------------------------------------------------------------

    private static boolean breached(Incident incident, Instant now) {
        SlaTracker sla = incident.getSla();
        return sla.isStarted() && (sla.isResponseBreached(now) || sla.isResolutionBreached(now));
    }

    /** Of the incidents whose SLA outcome is known (resolved, or already breached), the share that met both targets. */
    private static Double slaCompliance(List<Incident> list, Instant now) {
        long decided = count(list, i -> i.getSla().isStarted() && (i.getSla().getResolvedAt() != null || breached(i, now)));
        if (decided == 0) {
            return null;
        }
        long met = count(list, i -> i.getSla().isStarted() && i.getSla().getResolvedAt() != null && !breached(i, now));
        return round(100.0 * met / decided);
    }

    /** Minutes from the SLA clock starting (classification) to the technician's first response. */
    private static Double responseMinutes(Incident incident) {
        SlaTracker sla = incident.getSla();
        return sla.getStartedAt() == null || sla.getRespondedAt() == null ? null
                : minutesBetween(sla.getStartedAt(), sla.getRespondedAt());
    }

    /** Wall-clock minutes from reporting to resolution (mean time to resolve). */
    private static Double resolutionMinutes(Incident incident) {
        Instant resolvedAt = incident.getSla().getResolvedAt();
        return resolvedAt == null ? null : minutesBetween(incident.getCreatedAt(), resolvedAt);
    }

    private static Double rating(Incident incident) {
        return incident.getFeedback() != null && incident.getFeedback().getRating() != null
                ? incident.getFeedback().getRating().doubleValue() : null;
    }

    private static double minutesBetween(Instant from, Instant to) {
        return Duration.between(from, to).toSeconds() / 60.0;
    }

    // ----- helpers -----------------------------------------------------------------------------------------------

    private User requireStaff() {
        User user = currentUser.require();
        if (user.getRole() == Role.EMPLOYEE) {
            throw new AuthorizationException("Reports are available to support staff only");
        }
        return user;
    }

    Period period(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new InvalidInputException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new InvalidInputException("A report can cover at most " + MAX_DAYS + " days");
        }
        return new Period(start, end);
    }

    private List<Incident> load(Period period) {
        return incidents.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                period.from().atStartOfDay(ZoneOffset.UTC).toInstant(),
                period.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static LocalDate day(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static long count(List<Incident> list, Predicate<Incident> predicate) {
        return list.stream().filter(predicate).count();
    }

    private static Double average(List<Incident> list, Function<Incident, Double> metric) {
        OptionalDouble avg = list.stream().map(metric).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? round(avg.getAsDouble()) : null;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
