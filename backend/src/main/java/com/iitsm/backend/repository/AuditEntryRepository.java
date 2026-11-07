package com.iitsm.backend.repository;

import com.iitsm.backend.domain.audit.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByIncidentIdOrderByOccurredAtAscIdAsc(Long incidentId);

    Page<AuditEntry> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
