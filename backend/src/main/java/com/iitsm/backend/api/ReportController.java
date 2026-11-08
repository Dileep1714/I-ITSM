package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.ReportDtos.OverviewReport;
import com.iitsm.backend.api.dto.ReportDtos.ServiceReport;
import com.iitsm.backend.api.dto.ReportDtos.WorkloadReport;
import com.iitsm.backend.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Management reports for a period of whole days ({@code from}/{@code to} as {@code yyyy-MM-dd}, UTC, both
 * inclusive; default: the last 30 days). Support staff only.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public OverviewReport overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.overview(from, to);
    }

    @GetMapping("/technicians")
    public WorkloadReport technicians(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.technicianWorkload(from, to);
    }

    @GetMapping("/services")
    public ServiceReport services(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.serviceAnalysis(from, to);
    }
}
