package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitSchedulerTest {

    @Test
    void scheduleCallsTaskDirectlyWithoutTransaction() {
        AfterCommitScheduler scheduler = new AfterCommitScheduler();
        List<Long> received = new ArrayList<>();

        scheduler.schedule(List.of(1L, 2L, 3L), received::addAll);

        assertEquals(List.of(1L, 2L, 3L), received);
    }

    @Test
    void scheduleDeduplicatesAndFiltersNulls() {
        AfterCommitScheduler scheduler = new AfterCommitScheduler();
        List<Long> received = new ArrayList<>();

        scheduler.schedule(Arrays.asList(1L, 2L, 2L, null), received::addAll);

        assertEquals(List.of(1L, 2L), received);
    }

    @Test
    void scheduleSkipsWhenAllNullAfterDedup() {
        AfterCommitScheduler scheduler = new AfterCommitScheduler();
        List<Long> received = new ArrayList<>();

        scheduler.schedule(Arrays.asList(null, null), received::addAll);

        assertEquals(List.of(), received);
    }

    @Test
    void scheduleRunsTaskAfterCommitWhenTransactionSynchronizationActive() {
        AfterCommitScheduler scheduler = new AfterCommitScheduler();
        List<Long> received = new ArrayList<>();

        TransactionSynchronizationManager.initSynchronization();
        try {
            scheduler.schedule(List.of(1L, 2L), received::addAll);

            assertEquals(List.of(), received);

            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

            assertEquals(List.of(1L, 2L), received);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
