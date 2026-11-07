package com.iitsm.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Runs the SLA monitor in the background (the spec's "background processing for continuous SLA monitoring").
 * Interval set by {@code iitsm.sla.monitor-interval}; disabled in tests, which run cycles explicitly.
 */
@Component
@ConditionalOnProperty(name = "iitsm.sla.monitor-enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitorJob {

    private final SlaMonitorService monitor;
    private final Clock clock;

    public SlaMonitorJob(SlaMonitorService monitor, Clock clock) {
        this.monitor = monitor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${iitsm.sla.monitor-interval:60s}", initialDelayString = "${iitsm.sla.initial-delay:20s}")
    public void run() {
        monitor.runCycle(clock.instant());
    }
}
