package com.iitsm.backend.repository;

import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.sla.SlaViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaViolationRepository extends JpaRepository<SlaViolation, Long> {

    boolean existsByIncidentAndKind(Incident incident, SlaViolation.Kind kind);

    List<SlaViolation> findByIncident(Incident incident);

    Page<SlaViolation> findAllByOrderByDetectedAtDesc(Pageable pageable);
}
