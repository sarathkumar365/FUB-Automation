package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.LeadFeedPageResponse;
import com.fuba.automation_engine.controller.dto.LeadSummaryResponse;
import com.fuba.automation_engine.exception.lead.InvalidLeadFeedQueryException;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.service.lead.LeadAdminQueryService;
import com.fuba.automation_engine.service.lead.LeadAdminQueryService.LeadFeedQuery;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only admin endpoints for the local leads store.
 *
 * <p>Mirrors the style of {@link AdminWebhookController}: one cursor-paginated
 * list endpoint plus a single detail/summary endpoint that aggregates the
 * per-lead activity timeline. A separate "refresh from FUB" POST endpoint was
 * intentionally dropped — callers ask for a live refresh via the
 * {@code includeLive=true} query parameter on the summary endpoint, which keeps
 * local fallback logic co-located with the read path.
 */
@RestController
@RequestMapping("/admin/leads")
public class AdminLeadController {

    private final LeadAdminQueryService leadAdminQueryService;

    public AdminLeadController(LeadAdminQueryService leadAdminQueryService) {
        this.leadAdminQueryService = leadAdminQueryService;
    }

    @GetMapping
    public ResponseEntity<LeadFeedPageResponse> list(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String sourceLeadIdPrefix,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        LeadFeedPageResponse response = leadAdminQueryService.list(new LeadFeedQuery(
                sourceSystem,
                status,
                sourceLeadIdPrefix,
                from,
                to,
                limit,
                cursor));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sourceLeadId}/summary")
    public ResponseEntity<LeadSummaryResponse> summary(
            @PathVariable String sourceLeadId,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "false") boolean includeLive) {
        return leadAdminQueryService.summary(sourceLeadId, sourceSystem, includeLive)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    @ExceptionHandler(InvalidLeadFeedQueryException.class)
    public ResponseEntity<String> handleInvalidFeedQuery(InvalidLeadFeedQueryException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
