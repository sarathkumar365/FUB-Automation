package com.flux.controller;

import com.flux.controller.dto.AccountabilityReportDto;
import com.flux.controller.dto.AccountabilityReportDto.UnreachedLeads;
import com.flux.controller.dto.SourceContactReportDto;
import com.flux.service.reporting.ReportWindow;
import com.flux.service.reporting.UnknownReportWindowException;
import com.flux.service.reporting.accountability.AccountabilityReportService;
import com.flux.service.reporting.sourcecontact.SourceContactReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reporting")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class ReportingController {

    private final SourceContactReportService sourceContactReportService;
    private final AccountabilityReportService accountabilityReportService;

    public ReportingController(
            SourceContactReportService sourceContactReportService,
            AccountabilityReportService accountabilityReportService) {
        this.sourceContactReportService = sourceContactReportService;
        this.accountabilityReportService = accountabilityReportService;
    }

    /** @param window {@code yesterday} (default), {@code today}, or {@code this-week} */
    @GetMapping("/source-contact")
    public ResponseEntity<SourceContactReportDto> sourceContact(
            @RequestParam(required = false) String window) {
        return ResponseEntity.ok(sourceContactReportService.report(ReportWindow.fromKey(window)));
    }

    /** @param window {@code yesterday} (default), {@code today}, or {@code this-week} */
    @GetMapping("/accountability")
    public ResponseEntity<AccountabilityReportDto> accountability(
            @RequestParam(required = false) String window) {
        return ResponseEntity.ok(accountabilityReportService.report(ReportWindow.fromKey(window)));
    }

    /** The leads behind an agent's unreached count — name and contact handle, oldest first. */
    @GetMapping("/accountability/{agentId}/unreached")
    public ResponseEntity<UnreachedLeads> unreached(
            @PathVariable long agentId,
            @RequestParam(required = false) String window) {
        return ResponseEntity.ok(
                accountabilityReportService.unreached(ReportWindow.fromKey(window), agentId));
    }

    /**
     * An unknown {@code window} is bad input, not a server fault (mirrors the sibling admin
     * controllers). Scoped to its own exception type so a genuine internal fault still
     * surfaces as a 500 rather than being reported to the caller as their mistake.
     */
    @ExceptionHandler(UnknownReportWindowException.class)
    public ResponseEntity<String> handleUnknownWindow(UnknownReportWindowException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
