package com.iitsm.backend.service;

import com.iitsm.backend.common.exception.SlaPolicyMissingException;
import com.iitsm.backend.domain.catalog.ItService;
import com.iitsm.backend.domain.incident.PriorityLevel;
import com.iitsm.backend.domain.sla.SlaPolicy;
import com.iitsm.backend.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;

/** Finds the SLA that applies: the service-specific policy if one exists, otherwise the generic one. */
@Service
public class SlaPolicyResolver {

    private final SlaPolicyRepository policies;

    public SlaPolicyResolver(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    public SlaPolicy resolve(PriorityLevel priority, ItService service) {
        return policies.findByPriorityAndService(priority, service)
                .or(() -> policies.findByPriorityAndServiceIsNull(priority))
                .orElseThrow(() -> new SlaPolicyMissingException(priority.name(), service.getName()));
    }
}
