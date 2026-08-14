package com.flux.service.reporting.dashboard;

import com.flux.controller.dto.DashboardSnapshotDto;
import com.flux.controller.dto.DashboardSnapshotDto.CountStat;
import com.flux.controller.dto.DashboardSnapshotDto.Delta;
import com.flux.controller.dto.DashboardSnapshotDto.Direction;
import com.flux.controller.dto.DashboardSnapshotDto.FailureRow;
import com.flux.controller.dto.DashboardSnapshotDto.Funnel;
import com.flux.controller.dto.DashboardSnapshotDto.HealthState;
import com.flux.controller.dto.DashboardSnapshotDto.Hero;
import com.flux.controller.dto.DashboardSnapshotDto.HeroStats;
import com.flux.controller.dto.DashboardSnapshotDto.RateStat;
import com.flux.controller.dto.DashboardSnapshotDto.RunRow;
import com.flux.controller.dto.DashboardSnapshotDto.Stage;
import com.flux.controller.dto.DashboardSnapshotDto.Throughput;
import com.flux.controller.dto.DashboardSnapshotDto.Window;
import com.flux.controller.dto.WorkflowRunSummary;
import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.repository.DashboardMetricsReadRepository;
import com.flux.persistence.repository.DashboardMetricsReadRepository.FailedRunRow;
import com.flux.persistence.repository.DashboardMetricsReadRepository.HourlyBucket;
import com.flux.service.workflow.WorkflowRunQueryService;
import com.flux.service.workflow.WorkflowRunQueryService.ListRunsResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardSnapshotService {

    private static final int WINDOW_HOURS = 24;
    private static final int SPARK_POINTS = 8;
    private static final int RECENT_RUNS = 5;
    private static final int ATTENTION_CAP = 50;
    private static final long PER_MIN_SECONDS = 60;
    private static final double HEALTHY_THRESHOLD = 95.0;
    private static final double DEGRADED_THRESHOLD = 85.0;

    private final DashboardMetricsReadRepository readRepository;
    private final WorkflowRunQueryService workflowRunQueryService;
    private final Clock clock;

    public DashboardSnapshotService(
            DashboardMetricsReadRepository readRepository,
            WorkflowRunQueryService workflowRunQueryService,
            Clock clock) {
        this.readRepository = readRepository;
        this.workflowRunQueryService = workflowRunQueryService;
        this.clock = clock;
    }

    // REPEATABLE_READ + REQUIRED so every read sees one DB snapshot and the cross-widget invariant
    // (openFailures == funnel.failed == needsAttention total) holds; the reused query must stay REQUIRED (#31).
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public DashboardSnapshotDto snapshot() {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = now.truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime from = to.minusHours(WINDOW_HOURS);
        OffsetDateTime priorTo = from;
        OffsetDateTime priorFrom = from.minusHours(WINDOW_HOURS);

        Map<WorkflowRunStatus, Long> cur = readRepository.runStatusCounts(from, to);
        Map<WorkflowRunStatus, Long> prev = readRepository.runStatusCounts(priorFrom, priorTo);

        int[] ingested = dense(readRepository.hourlyIngested(from, to), from);
        int[] domainEvents = dense(readRepository.hourlyDomainEvents(from, to), from);
        int[] runsSeries = dense(readRepository.hourlyRunsTotal(from, to), from);
        int[] failedSeries = dense(readRepository.hourlyFailedRuns(from, to), from);

        long perMin = readRepository.countWebhookEventsSince(now.minusSeconds(PER_MIN_SECONDS));
        List<FailedRunRow> failedRows = readRepository.failedRuns(from, to, ATTENTION_CAP);
        List<WorkflowRunSummary> recent = recentRuns();

        long runsTotal = runsTotal(cur);
        long failedCount = cur.getOrDefault(WorkflowRunStatus.FAILED, 0L);
        Double successRate = successRate(cur);

        long prevRunsTotal = runsTotal(prev);
        long prevFailed = prev.getOrDefault(WorkflowRunStatus.FAILED, 0L);
        Double prevSuccessRate = successRate(prev);

        Hero hero = new Hero(
                healthState(successRate),
                failedCount,
                new HeroStats(
                        new CountStat(runsTotal, percentDelta(runsTotal, prevRunsTotal)),
                        new RateStat(successRate, pointDelta(successRate, prevSuccessRate)),
                        new CountStat(failedCount, countDelta(failedCount, prevFailed))));

        Throughput throughput = new Throughput(ingested, max(ingested), mean(ingested), perMin);

        // failedCount is reused for hero.openFailures AND funnel.failed.value so the invariant holds by construction.
        Funnel funnel = new Funnel(
                new Stage(sum(ingested), spark(ingested)),
                new Stage(sum(domainEvents), spark(domainEvents)),
                new Stage(runsTotal, spark(runsSeries)),
                new Stage(failedCount, spark(failedSeries)));

        return new DashboardSnapshotDto(
                new Window(from, to, "Last 24h"),
                hero,
                throughput,
                funnel,
                recent.stream().map(this::toRunRow).toList(),
                failedRows.stream().map(row -> toFailureRow(row, now)).toList());
    }

    private List<WorkflowRunSummary> recentRuns() {
        ListRunsResult result = workflowRunQueryService.listRunsCrossWorkflow(null, 0, RECENT_RUNS);
        return result.page() == null ? List.of() : result.page().items();
    }

    private long runsTotal(Map<WorkflowRunStatus, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return total - counts.getOrDefault(WorkflowRunStatus.DUPLICATE_IGNORED, 0L);
    }

    private Double successRate(Map<WorkflowRunStatus, Long> counts) {
        long completed = counts.getOrDefault(WorkflowRunStatus.COMPLETED, 0L);
        long failed = counts.getOrDefault(WorkflowRunStatus.FAILED, 0L);
        long terminal = completed + failed;
        return terminal == 0 ? null : (completed * 100.0) / terminal;
    }

    private HealthState healthState(Double successRate) {
        if (successRate == null || successRate >= HEALTHY_THRESHOLD) {
            return HealthState.HEALTHY;
        }
        return successRate >= DEGRADED_THRESHOLD ? HealthState.DEGRADED : HealthState.UNHEALTHY;
    }

    private Delta percentDelta(long cur, long prev) {
        if (prev == 0) {
            return new Delta(null, Direction.FLAT);
        }
        return delta(((double) (cur - prev) / prev) * 100.0);
    }

    private Delta pointDelta(Double cur, Double prev) {
        if (cur == null || prev == null) {
            return new Delta(null, Direction.FLAT);
        }
        return delta(cur - prev);
    }

    // Absolute count delta — always cur−prev so a 0→N failure spike shows UP (no zero-baseline guard;
    // unlike the ratio deltas there is no division to protect against).
    private Delta countDelta(long cur, long prev) {
        return delta((double) (cur - prev));
    }

    private Delta delta(double value) {
        Direction direction = value > 0 ? Direction.UP : value < 0 ? Direction.DOWN : Direction.FLAT;
        return new Delta(value, direction);
    }

    private int[] dense(List<HourlyBucket> buckets, OffsetDateTime from) {
        // Key by instant, not OffsetDateTime, so bucket matching is robust to offset representation.
        Map<Instant, Long> byHour = new HashMap<>();
        for (HourlyBucket bucket : buckets) {
            byHour.put(bucket.hour().toInstant(), bucket.count());
        }
        int[] series = new int[WINDOW_HOURS];
        for (int i = 0; i < WINDOW_HOURS; i++) {
            series[i] = byHour.getOrDefault(from.plusHours(i).toInstant(), 0L).intValue();
        }
        return series;
    }

    private int[] spark(int[] series) {
        return Arrays.copyOfRange(series, WINDOW_HOURS - SPARK_POINTS, WINDOW_HOURS);
    }

    private long sum(int[] series) {
        long total = 0;
        for (int value : series) {
            total += value;
        }
        return total;
    }

    private int max(int[] series) {
        int peak = 0;
        for (int value : series) {
            peak = Math.max(peak, value);
        }
        return peak;
    }

    private int mean(int[] series) {
        return series.length == 0 ? 0 : (int) (sum(series) / series.length);
    }

    private RunRow toRunRow(WorkflowRunSummary summary) {
        Long durationSec = summary.startedAt() != null && summary.completedAt() != null
                ? Duration.between(summary.startedAt(), summary.completedAt()).toSeconds()
                : null;
        return new RunRow(summary.id(), summary.workflowKey(), summary.status(),
                summary.startedAt(), summary.completedAt(), durationSec);
    }

    private FailureRow toFailureRow(FailedRunRow row, OffsetDateTime now) {
        long ageSeconds = Duration.between(row.createdAt(), now).toSeconds();
        return new FailureRow(String.valueOf(row.id()), row.workflowKey(), "FAILED", row.reasonCode(), ageSeconds);
    }
}
