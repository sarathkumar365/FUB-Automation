package com.fuba.automation_engine.service.event;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEngineWriteTrackerTest {

    private static final String ENTITY_TYPE = "person";
    private static final String ENTITY_ID = "20235";

    @Test
    void recordReturnsRecordWithAssignedIdAndTimestamp() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);

        EngineWriteRecord record = tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 42L);

        assertTrue(record.id() > 0);
        assertEquals(ENTITY_TYPE, record.entityType());
        assertEquals(ENTITY_ID, record.entityId());
        assertEquals(Set.of("assignedUserId"), record.changedFields());
        assertEquals(42L, record.runId());
        assertNotNull(record.recordedAt());
    }

    @Test
    void findMatchingExactSetHits() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        Optional<EngineWriteRecord> hit = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), OffsetDateTime.now());

        assertTrue(hit.isPresent());
        assertEquals(Set.of("assignedUserId"), hit.get().changedFields());
    }

    @Test
    void findMatchingSubsetHits_engineFieldsAreSubsetOfDiff() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        Optional<EngineWriteRecord> hit = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID,
                Set.of("assignedUserId", "lastActivity"),
                OffsetDateTime.now());

        assertTrue(hit.isPresent(), "engine fields ⊆ diff fields must be a hit");
    }

    @Test
    void findMatchingSupersetMisses_engineWroteMoreThanDiffShows() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        tracker.record(ENTITY_TYPE, ENTITY_ID,
                Set.of("assignedUserId", "assignedPondId"), 1L);

        Optional<EngineWriteRecord> miss = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), OffsetDateTime.now());

        assertTrue(miss.isEmpty(), "engine fields ⊃ diff fields must be a miss");
    }

    @Test
    void findMatchingDisjointMisses() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        Optional<EngineWriteRecord> miss = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID, Set.of("tags"), OffsetDateTime.now());

        assertTrue(miss.isEmpty());
    }

    @Test
    void findMatchingDifferentEntityMisses() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        // Same entityType, different entityId
        Optional<EngineWriteRecord> diffEntity = tracker.findMatching(
                ENTITY_TYPE, "99999", Set.of("assignedUserId"), OffsetDateTime.now());
        assertTrue(diffEntity.isEmpty());

        // Different entityType, same entityId
        Optional<EngineWriteRecord> diffType = tracker.findMatching(
                "note", ENTITY_ID, Set.of("assignedUserId"), OffsetDateTime.now());
        assertTrue(diffType.isEmpty());
    }

    @Test
    void findMatchingExpiredRecordMisses() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(1);
        EngineWriteRecord record = tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        OffsetDateTime future = record.recordedAt().plusSeconds(2);
        Optional<EngineWriteRecord> miss = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), future);

        assertTrue(miss.isEmpty(), "expired record must not match");
    }

    @Test
    void findMatchingMostRecentRecordWinsAmongSubsetMatches() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        EngineWriteRecord first = tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);
        sleepMicros(2000);
        EngineWriteRecord second = tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 2L);

        Optional<EngineWriteRecord> hit = tracker.findMatching(
                ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), OffsetDateTime.now());

        assertTrue(hit.isPresent());
        assertEquals(second.id(), hit.get().id(), "most recent record must win");
        assertTrue(first.recordedAt().isBefore(second.recordedAt()));
    }

    @Test
    void evictExpiredRemovesOnlyOldRecords() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(1);
        EngineWriteRecord old = tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 1L);

        sleepMicros(1100);
        OffsetDateTime futureSoEvict = old.recordedAt().plusSeconds(2);
        tracker.evictExpired(futureSoEvict);
        assertEquals(0, tracker.size(), "expired record must be evicted");

        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("tags"), 2L);
        tracker.evictExpired(OffsetDateTime.now());
        assertEquals(1, tracker.size(), "fresh record must NOT be evicted");
    }

    @Test
    void concurrentRecordAndFindIsThreadSafe() throws InterruptedException {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        int threads = 20;
        int recordsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < recordsPerThread; i++) {
                        tracker.record(ENTITY_TYPE, String.valueOf(threadIndex),
                                Set.of("assignedUserId"), (long) i);
                        tracker.findMatching(ENTITY_TYPE, String.valueOf(threadIndex),
                                Set.of("assignedUserId"), OffsetDateTime.now());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent test timed out");
        pool.shutdown();

        assertEquals(threads * recordsPerThread, tracker.size(),
                "all records from all threads must have been persisted without loss");
    }

    @Test
    void recordWithNullChangedFieldsThrows() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.record(ENTITY_TYPE, ENTITY_ID, null, 1L));
    }

    @Test
    void recordWithEmptyChangedFieldsThrows() {
        InMemoryEngineWriteTracker tracker = new InMemoryEngineWriteTracker(30);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of(), 1L));
    }

    private static void sleepMicros(long micros) {
        try {
            Thread.sleep(micros / 1000, (int) ((micros % 1000) * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
