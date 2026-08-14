package com.flux.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardSnapshotDto(
        Window window,
        Hero hero,
        Throughput throughput,
        Funnel funnel,
        List<RunRow> recentRuns,
        List<FailureRow> needsAttention) {

    public record Window(OffsetDateTime from, OffsetDateTime to, String label) {}

    public record Hero(HealthState state, long openFailures, HeroStats stats) {}

    public record HeroStats(CountStat runs, RateStat successRate, CountStat openFailures) {}

    public record CountStat(long value, Delta delta) {}

    public record RateStat(Double value, Delta delta) {}

    public record Delta(Double value, Direction direction) {}

    public record Throughput(int[] series, int peak, int avg, long perMin) {}

    public record Funnel(Stage ingested, Stage domainEvents, Stage runs, Stage failed) {}

    public record Stage(long value, int[] spark) {}

    public record RunRow(Long id, String workflowKey, String status,
                         OffsetDateTime startedAt, OffsetDateTime completedAt, Long durationSec) {}

    public record FailureRow(String ref, String workflowKey, String status, String reason, long ageSeconds) {}

    public enum HealthState { HEALTHY, DEGRADED, UNHEALTHY }

    public enum Direction { UP, DOWN, FLAT }
}
