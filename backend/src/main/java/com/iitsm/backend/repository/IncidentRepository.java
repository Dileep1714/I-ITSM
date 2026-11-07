package com.iitsm.backend.repository;

import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.user.Employee;
import com.iitsm.backend.domain.user.Technician;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Incident persistence. {@link JpaSpecificationExecutor} enables the Specification-based search (combining any of
 * the search filters at runtime).
 */
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @Query(value = "SELECT nextval('incident_number_seq')", nativeQuery = true)
    long nextIncidentNumber();

    /** Ids only: the SLA monitor then processes each incident in its own short transaction. */
    @Query("SELECT i.id FROM Incident i WHERE i.status IN :statuses ORDER BY i.id")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<IncidentStatus> statuses);

    Page<Incident> findByReporter(Employee reporter, Pageable pageable);

    Page<Incident> findByAssignee(Technician assignee, Pageable pageable);

    /** Current workload per technician: incidents assigned to them in one of the given (active) statuses. */
    @Query("""
            SELECT new com.iitsm.backend.repository.TechnicianCount(i.assignee.id, COUNT(i))
            FROM Incident i
            WHERE i.assignee IS NOT NULL AND i.status IN :statuses
            GROUP BY i.assignee.id
            """)
    List<TechnicianCount> countByAssigneeAndStatusIn(@Param("statuses") Collection<IncidentStatus> statuses);

    /** How many incidents each technician has resolved on one service: their experience with that system. */
    @Query("""
            SELECT new com.iitsm.backend.repository.TechnicianCount(i.resolution.resolvedBy.id, COUNT(i))
            FROM Incident i
            WHERE i.service = :service AND i.resolution.resolvedBy IS NOT NULL
            GROUP BY i.resolution.resolvedBy.id
            """)
    List<TechnicianCount> countResolvedByTechnicianOnService(@Param("service") ItService service);
}
