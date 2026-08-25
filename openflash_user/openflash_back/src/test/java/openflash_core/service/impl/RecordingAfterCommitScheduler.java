package openflash_core.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

class RecordingAfterCommitScheduler extends AfterCommitScheduler {
    List<Long> scheduledIds = List.of();

    @Override
    public void schedule(Collection<Long> cardIds, Consumer<List<Long>> task) {
        scheduledIds = List.copyOf(cardIds);
        task.accept(scheduledIds);
    }
}
