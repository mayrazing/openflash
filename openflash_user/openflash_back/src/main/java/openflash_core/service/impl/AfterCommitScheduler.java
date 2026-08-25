package openflash_core.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterCommitScheduler {

    public void schedule(Collection<Long> cardIds, Consumer<List<Long>> task) {
        List<Long> ids = cardIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.accept(ids);
                }
            });
            return;
        }
        task.accept(ids);
    }
}
