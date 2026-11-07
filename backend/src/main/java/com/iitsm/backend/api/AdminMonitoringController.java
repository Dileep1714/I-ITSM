package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.IncidentDtos.AuditEntryView;
import com.iitsm.backend.api.dto.MonitoringDtos.SlaViolationView;
import com.iitsm.backend.api.dto.PageResponse;
import com.iitsm.backend.notification.EmailSender;
import com.iitsm.backend.notification.OutboundMessageLog;
import com.iitsm.backend.repository.AuditEntryRepository;
import com.iitsm.backend.repository.SlaViolationRepository;
import com.iitsm.backend.service.SlaMonitorService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Administrator monitoring: the global audit log, recorded SLA violations, the outbox of simulated emails and
 * text messages, and a button to run the SLA monitor immediately. Admin only, via the {@code /api/admin/**} rule.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMonitoringController {

    private final AuditEntryRepository auditEntries;
    private final SlaViolationRepository violations;
    private final OutboundMessageLog outbox;
    private final EmailSender emailSender;
    private final SlaMonitorService slaMonitor;
    private final Clock clock;

    public AdminMonitoringController(AuditEntryRepository auditEntries, SlaViolationRepository violations,
                                     OutboundMessageLog outbox, EmailSender emailSender,
                                     SlaMonitorService slaMonitor, Clock clock) {
        this.auditEntries = auditEntries;
        this.violations = violations;
        this.outbox = outbox;
        this.emailSender = emailSender;
        this.slaMonitor = slaMonitor;
        this.clock = clock;
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryView> audit(@PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(auditEntries.findAllByOrderByOccurredAtDescIdDesc(pageable), AuditEntryView::of);
    }

    @GetMapping("/sla-violations")
    @Transactional(readOnly = true)
    public PageResponse<SlaViolationView> slaViolations(@PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(violations.findAllByOrderByDetectedAtDesc(pageable), SlaViolationView::of);
    }

    /** Recent emails and SMS, so the demo can show exactly what was sent. */
    @GetMapping("/outbox")
    public Map<String, Object> outbox(@RequestParam(defaultValue = "50") int limit) {
        List<OutboundMessageLog.OutboundMessage> recent = outbox.recent(Math.clamp(limit, 1, 200));
        return Map.of("emailSimulated", emailSender.isSimulated(), "messages", recent);
    }

    /** Run one SLA monitor cycle now instead of waiting for the next scheduled run. */
    @PostMapping("/sla-monitor/run")
    public SlaMonitorService.CycleReport runSlaMonitor() {
        return slaMonitor.runCycle(clock.instant());
    }
}
