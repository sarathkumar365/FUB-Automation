package com.flux.service.reporting.dashboard;

import com.flux.controller.dto.DashboardSnapshotDto;
import com.flux.controller.dto.DashboardSnapshotDto.Direction;
import com.flux.controller.dto.DashboardSnapshotDto.HealthState;
import com.flux.controller.dto.WorkflowRunSummary;
import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.repository.DashboardMetricsReadRepository;
import com.flux.persistence.repository.DashboardMetricsReadRepository.FailedRunRow;
import com.flux.persistence.repository.DashboardMetricsReadRepository.HourlyBucket;
import com.flux.service.workflow.WorkflowRunQueryService;
import com.flux.service.workflow.WorkflowRunQueryService.ListRunsResult;
import com.flux.service.workflow.WorkflowRunQueryService.ListRunsStatus;
import com.flux.service.workflow.WorkflowRunQueryService.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSnapshotServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-01T10:30:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-02-01T10:00:00Z");
    private static final OffsetDateTime FROM = TO.minusHours(24);
    private static final OffsetDateTime PRIOR_TO = FROM;
    private static final OffsetDateTime PRIOR_FROM = FROM.minusHours(24);

    @Mock
    private DashboardMetricsReadRepository readRepository;

    @Mock
    private WorkflowRunQueryService workflowRunQueryService;

    private DashboardSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSnapshotService(readRepository, workflowRunQueryService, CLOCK);
        lenient().when(workflowRunQueryService.listRunsCrossWorkflow(null, 0, 5))
                .thenReturn(new ListRunsResult(ListRunsStatus.SUCCESS, new PageResult<>(List.of(), 0, 5, 0), null));
    }

    @Test
    void healthy_withDeltas_andInvariant() {
        stubCurrent(Map.of(WorkflowRunStatus.COMPLETED, 97L, WorkflowRunStatus.FAILED, 3L));
        stubPrior(Map.of(WorkflowRunStatus.COMPLETED, 90L, WorkflowRunStatus.FAILED, 10L));

        DashboardSnapshotDto dto = service.snapshot();

        assertThat(dto.hero().state()).isEqualTo(HealthState.HEALTHY);
        assertThat(dto.hero().stats().successRate().value()).isEqualTo(97.0);
        assertThat(dto.hero().openFailures()).isEqualTo(3L);
        assertThat(dto.funnel().failed().value()).isEqualTo(3L); // invariant: == hero.openFailures
        assertThat(dto.hero().stats().runs().delta())
                .isEqualTo(new DashboardSnapshotDto.Delta(0.0, Direction.FLAT));
        assertThat(dto.hero().stats().successRate().delta())
                .isEqualTo(new DashboardSnapshotDto.Delta(7.0, Direction.UP));
        assertThat(dto.hero().stats().openFailures().delta())
                .isEqualTo(new DashboardSnapshotDto.Delta(-7.0, Direction.DOWN));
        assertThat(dto.window().from()).isEqualTo(FROM);
        assertThat(dto.window().to()).isEqualTo(TO);
    }

    @Test
    void degraded_whenRateBetween85And95() {
        stubCurrent(Map.of(WorkflowRunStatus.COMPLETED, 90L, WorkflowRunStatus.FAILED, 10L));
        stubPrior(Map.of());

        assertThat(service.snapshot().hero().state()).isEqualTo(HealthState.DEGRADED);
    }

    @Test
    void unhealthy_whenRateBelow85() {
        stubCurrent(Map.of(WorkflowRunStatus.COMPLETED, 80L, WorkflowRunStatus.FAILED, 20L));
        stubPrior(Map.of());

        assertThat(service.snapshot().hero().state()).isEqualTo(HealthState.UNHEALTHY);
    }

    @Test
    void noTerminalRuns_isHealthy_withNullRate() {
        stubCurrent(Map.of(WorkflowRunStatus.PENDING, 5L));
        stubPrior(Map.of());

        DashboardSnapshotDto dto = service.snapshot();

        assertThat(dto.hero().state()).isEqualTo(HealthState.HEALTHY);
        assertThat(dto.hero().stats().successRate().value()).isNull();
        assertThat(dto.hero().openFailures()).isZero();
        assertThat(dto.hero().stats().runs().value()).isEqualTo(5L); // PENDING counts toward runs total
    }

    @Test
    void priorZeroBaseline_ratioDeltasFlat_butFailureSpikeShown() {
        stubCurrent(Map.of(WorkflowRunStatus.COMPLETED, 10L, WorkflowRunStatus.FAILED, 2L));
        stubPrior(Map.of());

        DashboardSnapshotDto dto = service.snapshot();

        DashboardSnapshotDto.Delta nullFlat = new DashboardSnapshotDto.Delta(null, Direction.FLAT);
        assertThat(dto.hero().stats().runs().delta()).isEqualTo(nullFlat);        // % change has no zero baseline
        assertThat(dto.hero().stats().successRate().delta()).isEqualTo(nullFlat); // no prior terminal runs
        assertThat(dto.hero().stats().openFailures().delta())                     // absolute spike IS surfaced
                .isEqualTo(new DashboardSnapshotDto.Delta(2.0, Direction.UP));
    }

    @Test
    void zeroFills_seriesTo24Points_andSparkToLast8() {
        stubCurrent(Map.of());
        stubPrior(Map.of());
        when(readRepository.hourlyIngested(FROM, TO)).thenReturn(List.of(
                new HourlyBucket(FROM, 5L),
                new HourlyBucket(FROM.plusHours(23), 7L)));

        DashboardSnapshotDto dto = service.snapshot();

        assertThat(dto.throughput().series()).hasSize(24);
        assertThat(dto.throughput().series()[0]).isEqualTo(5);
        assertThat(dto.throughput().series()[23]).isEqualTo(7);
        assertThat(dto.throughput().peak()).isEqualTo(7);
        assertThat(dto.funnel().ingested().value()).isEqualTo(12L);
        assertThat(dto.funnel().ingested().spark()).hasSize(8);
        assertThat(dto.funnel().ingested().spark()[7]).isEqualTo(7); // last bucket
    }

    @Test
    void mapsRecentRuns_andNeedsAttention_withDerivedTimes() {
        stubCurrent(Map.of());
        stubPrior(Map.of());
        OffsetDateTime started = TO.minusHours(1);
        when(workflowRunQueryService.listRunsCrossWorkflow(null, 0, 5)).thenReturn(new ListRunsResult(
                ListRunsStatus.SUCCESS,
                new PageResult<>(List.of(
                        new WorkflowRunSummary(1L, "wf-a", 1L, "COMPLETED", null, started, started.plusSeconds(42))),
                        0, 5, 1),
                null));
        when(readRepository.failedRuns(FROM, TO, 50)).thenReturn(List.of(
                new FailedRunRow(7L, "wf-b", null, OffsetDateTime.parse("2026-02-01T10:25:00Z"))));

        DashboardSnapshotDto dto = service.snapshot();

        assertThat(dto.recentRuns()).singleElement().satisfies(run -> {
            assertThat(run.id()).isEqualTo(1L);
            assertThat(run.durationSec()).isEqualTo(42L);
        });
        assertThat(dto.needsAttention()).singleElement().satisfies(row -> {
            assertThat(row.ref()).isEqualTo("7");
            assertThat(row.status()).isEqualTo("FAILED");
            assertThat(row.reason()).isNull();
            assertThat(row.ageSeconds()).isEqualTo(300L); // 10:30 now - 10:25 created
        });
    }

    @Test
    void snapshotMethod_declaresRepeatableReadRequiredReadOnly() throws NoSuchMethodException {
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(
                DashboardSnapshotService.class.getMethod("snapshot"), DashboardSnapshotService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(attribute.isReadOnly()).isTrue();
    }

    private void stubCurrent(Map<WorkflowRunStatus, Long> counts) {
        when(readRepository.runStatusCounts(FROM, TO)).thenReturn(counts);
    }

    private void stubPrior(Map<WorkflowRunStatus, Long> counts) {
        when(readRepository.runStatusCounts(PRIOR_FROM, PRIOR_TO)).thenReturn(counts);
    }
}
