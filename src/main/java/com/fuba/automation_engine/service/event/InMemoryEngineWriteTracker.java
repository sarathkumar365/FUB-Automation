package com.fuba.automation_engine.service.event;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InMemoryEngineWriteTracker implements EngineWriteTracker {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEngineWriteTracker.class);

    private final ConcurrentHashMap<TrackerKey, List<EngineWriteRecord>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);
    private final Duration ttl;

    public InMemoryEngineWriteTracker(
            @Value("${engine.write.tracker.ttl-seconds:30}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public EngineWriteRecord record(
            String entityType, String entityId, Set<String> changedFields, Long runId) {
        EngineWriteRecord record = new EngineWriteRecord(
                idSequence.incrementAndGet(),
                entityType,
                entityId,
                changedFields,
                runId,
                OffsetDateTime.now());

        store.compute(new TrackerKey(entityType, entityId), (key, existing) -> {
            List<EngineWriteRecord> list = (existing == null) ? new ArrayList<>(2) : existing;
            list.add(record);
            return list;
        });

        log.info("Tracker recorded entityType={} entityId={} changedFields={} runId={} recordId={}",
                entityType, entityId, changedFields, runId, record.id());
        return record;
    }

    @Override
    public Optional<EngineWriteRecord> findMatching(
            String entityType, String entityId, Set<String> diffFields, OffsetDateTime now) {

        OffsetDateTime threshold = now.minus(ttl);
        EngineWriteRecord match = matchInBucket(new TrackerKey(entityType, entityId), threshold, diffFields);

        if (match != null) {
            log.info("Tracker hit entityType={} entityId={} diffFields={} matchedRecordId={} matchedFields={}",
                    entityType, entityId, diffFields, match.id(), match.changedFields());
            return Optional.of(match);
        }
        log.debug("Tracker miss entityType={} entityId={} diffFields={}", entityType, entityId, diffFields);
        return Optional.empty();
    }

    // Search inside compute() — per-bucket lock prevents mid-iteration mutation.
    private EngineWriteRecord matchInBucket(TrackerKey key, OffsetDateTime threshold, Set<String> diffFields) {
        EngineWriteRecord[] holder = new EngineWriteRecord[1];
        store.compute(key, (k, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return existing;
            }
            EngineWriteRecord best = null;
            for (EngineWriteRecord r : existing) {
                if (r.recordedAt().isBefore(threshold)) continue;
                if (!diffFields.containsAll(r.changedFields())) continue;
                if (best == null || r.recordedAt().isAfter(best.recordedAt())) {
                    best = r;
                }
            }
            holder[0] = best;
            return existing;
        });
        return holder[0];
    }

    @Scheduled(fixedDelayString = "${engine.write.tracker.eviction-interval-ms:10000}")
    public void scheduledEviction() {
        evictExpired(OffsetDateTime.now());
    }

    @Override
    public void evictExpired(OffsetDateTime now) {
        OffsetDateTime threshold = now.minus(ttl);
        int[] evicted = {0};

        for (TrackerKey key : store.keySet()) {
            store.compute(key, (k, existing) -> {
                if (existing == null || existing.isEmpty()) {
                    return null;
                }
                List<EngineWriteRecord> kept = new ArrayList<>(existing.size());
                for (EngineWriteRecord r : existing) {
                    if (r.recordedAt().isBefore(threshold)) {
                        evicted[0]++;
                    } else {
                        kept.add(r);
                    }
                }
                return kept.isEmpty() ? null : kept;
            });
        }

        if (evicted[0] > 0) {
            log.info("Tracker evicted {} expired records (ttl={}s)", evicted[0], ttl.toSeconds());
        }
    }

    int size() {
        return store.values().stream().mapToInt(List::size).sum();
    }

    private record TrackerKey(String entityType, String entityId) {}
}
