package com.iitsm.backend.repository;

import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    /** The policy specific to one service, if there is one. */
    Optional<SlaPolicy> findByPriorityAndService(PriorityLevel priority, ItService service);

    /** The generic policy that applies to every service. */
    Optional<SlaPolicy> findByPriorityAndServiceIsNull(PriorityLevel priority);

    List<SlaPolicy> findAllByOrderByPriority();
}
