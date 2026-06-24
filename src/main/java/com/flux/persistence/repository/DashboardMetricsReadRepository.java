package com.flux.persistence.repository;

import com.flux.persistence.entity.WorkflowRunStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Dashboard-local read repo for the Phase-1 snapshot — deliberately not the RD-012 ReportingQuery port. */
public interface DashboardMetricsReadRepository {

    /** Statuses with no rows in the window are absent from the map (the caller treats absent as 0). */
    Map<WorkflowRunStatus, Long> runStatusCounts(OffsetDateTime from, OffsetDateTime to);

    List<HourlyBucket> hourlyIngested(OffsetDateTime from, OffsetDateTime to);

    List<HourlyBucket> hourlyDomainEvents(OffsetDateTime from, OffsetDateTime to);

    List<HourlyBucket> hourlyRunsTotal(OffsetDateTime from, OffsetDateTime to);

    List<HourlyBucket> hourlyFailedRuns(OffsetDateTime from, OffsetDateTime to);

    long countWebhookEventsSince(OffsetDateTime since);

    List<FailedRunRow> failedRuns(OffsetDateTime from, OffsetDateTime to, int limit);

    /** Only non-empty hours are returned; the service zero-fills the rest. */
    record HourlyBucket(OffsetDateTime hour, long count) {}

    record FailedRunRow(Long id, String workflowKey, String reasonCode, OffsetDateTime createdAt) {}
}
