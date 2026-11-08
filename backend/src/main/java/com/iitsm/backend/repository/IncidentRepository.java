package com.iitsm.backend.repository;

import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.iitsm.backend.domain.user.Employee;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Incident persistence. {@link JpaSpecificationExecutor} enables the Specification-based search (combining any of
 * the search filters at runtime).
 */
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @Query(value = "SELECT nextval('incident_number_seq')", nativeQuery = true)
    long nextIncidentNumber();

    // ----- intelligent features: candidate sets (bounded by time and size, newest first) -----------------------

    /** Recent incidents to compare a new or existing incident against (duplicates, similar, past fixes). */
    List<Incident> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since, Pageable limit);

    /** Recent incidents that already have a category: the "training data" for category suggestions. */
    List<Incident> findByCategoryIsNotNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since,
                                                                                            Pageable limit);

    /** Open incidents in a time window, for root-cause correlation. */
    List<Incident> findByStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Collection<IncidentStatus> statuses, Instant since);

    /** A reporter's own incidents in the given statuses, for the "you already reported this" check. */
    List<Incident> findByReporterAndStatusIn(Employee reporter, Collection<IncidentStatus> statuses);

    // ----- reports ------------------------------------------------------------------------------------------------

    /** Every incident created in [from, to). Reports aggregate in Java: simple, and fast enough at this scale. */
    List<Incident> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /** Ids only: the SLA monitor then processes each incident in its own short transaction. */
    @Query("SELECT i.id FROM Incident i WHERE i.status IN :statuses ORDER BY i.id")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<IncidentStatus> statuses);

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
