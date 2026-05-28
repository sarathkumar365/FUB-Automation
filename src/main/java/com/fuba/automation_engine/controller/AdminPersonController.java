package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.PersonFeedPageResponse;
import com.fuba.automation_engine.controller.dto.PersonSummaryResponse;
import com.fuba.automation_engine.exception.person.InvalidPersonFeedQueryException;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.service.person.PersonAdminQueryService;
import com.fuba.automation_engine.service.person.PersonAdminQueryService.PersonFeedQuery;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only admin endpoints for the local persons store.
 *
 * <p>Mirrors the style of {@link AdminWebhookController}: one cursor-paginated
 * list endpoint plus a single detail/summary endpoint that aggregates the
 * per-person activity timeline. A separate "refresh from FUB" POST endpoint was
 * intentionally dropped — callers ask for a live refresh via the
 * {@code includeLive=true} query parameter on the summary endpoint, which keeps
 * local fallback logic co-located with the read path.
 */
@RestController
@RequestMapping("/admin/persons")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class AdminPersonController {

    private final PersonAdminQueryService personAdminQueryService;

    public AdminPersonController(PersonAdminQueryService personAdminQueryService) {
        this.personAdminQueryService = personAdminQueryService;
    }

    @GetMapping
    public ResponseEntity<PersonFeedPageResponse> list(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) PersonStatus status,
            @RequestParam(required = false) String sourcePersonIdPrefix,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        PersonFeedPageResponse response = personAdminQueryService.list(new PersonFeedQuery(
                sourceSystem,
                status,
                sourcePersonIdPrefix,
                from,
                to,
                limit,
                cursor));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sourcePersonId}/summary")
    public ResponseEntity<PersonSummaryResponse> summary(
            @PathVariable String sourcePersonId,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(defaultValue = "false") boolean includeLive) {
        return personAdminQueryService.summary(sourcePersonId, sourceSystem, includeLive)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
    }

    @ExceptionHandler(InvalidPersonFeedQueryException.class)
    public ResponseEntity<String> handleInvalidFeedQuery(InvalidPersonFeedQueryException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
