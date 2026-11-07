package com.iitsm.backend.service;

import com.iitsm.backend.domain.assignment.AssignmentCandidate;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.user.Technician;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.repository.TechnicianCount;
import com.iitsm.backend.repository.TechnicianRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gathers the facts assignment strategies need: every active technician, their current workload and their past
 * fixes on the incident's service. Two aggregate queries in total, however many technicians there are (no N+1
 * queries).
 */
@Service
public class AssignmentCandidateService {

    static final Set<IncidentStatus> WORKLOAD_STATUSES = Arrays.stream(IncidentStatus.values())
            .filter(IncidentStatus::countsTowardsWorkload)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(IncidentStatus.class)));

    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;

    public AssignmentCandidateService(TechnicianRepository technicians, IncidentRepository incidents) {
        this.technicians = technicians;
        this.incidents = incidents;
    }

    public List<AssignmentCandidate> candidatesFor(Incident incident) {
        Map<Long, Long> workload = toMap(incidents.countByAssigneeAndStatusIn(WORKLOAD_STATUSES));
        Map<Long, Long> experience = toMap(incidents.countResolvedByTechnicianOnService(incident.getService()));
        return technicians.findByActiveTrueOrderByFullName().stream()
                .map(technician -> new AssignmentCandidate(technician,
                        workload.getOrDefault(technician.getId(), 0L).intValue(),
                        experience.getOrDefault(technician.getId(), 0L).intValue()))
                .toList();
    }

    public int activeIncidentsOf(Technician technician) {
        return toMap(incidents.countByAssigneeAndStatusIn(WORKLOAD_STATUSES))
                .getOrDefault(technician.getId(), 0L).intValue();
    }

    /** Active incident count for every technician, for the workload report. */
    public Map<Long, Long> workloadByTechnician() {
        return toMap(incidents.countByAssigneeAndStatusIn(WORKLOAD_STATUSES));
    }

    private static Map<Long, Long> toMap(List<TechnicianCount> counts) {
        return counts.stream().collect(Collectors.toMap(TechnicianCount::technicianId, TechnicianCount::count));
    }
}
