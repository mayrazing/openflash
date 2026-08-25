package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.entity.AsyncTask;
import openflash_core.service.AsyncTaskHandler;

class AsyncTaskHandlerRegistryTest {

    @Test
    void getRequiredReturnsRegisteredHandler() {
        StubHandler handler = new StubHandler("TASK_A");
        AsyncTaskHandlerRegistry registry = new AsyncTaskHandlerRegistry(List.of(handler));

        assertSame(handler, registry.getRequired("TASK_A"));
    }

    @Test
    void getRequiredRejectsUnknownTaskType() {
        AsyncTaskHandlerRegistry registry = new AsyncTaskHandlerRegistry(List.of(new StubHandler("TASK_A")));

        assertThrows(IllegalArgumentException.class, () -> registry.getRequired("TASK_B"));
    }

    @Test
    void constructorRejectsBlankTaskType() {
        assertThrows(IllegalStateException.class, () -> new AsyncTaskHandlerRegistry(List.of(new StubHandler(" "))));
    }

    @Test
    void constructorRejectsDuplicateTaskType() {
        assertThrows(
            IllegalStateException.class,
            () -> new AsyncTaskHandlerRegistry(List.of(new StubHandler("TASK_A"), new StubHandler("TASK_A")))
        );
    }

    private static final class StubHandler implements AsyncTaskHandler {
        private final String taskType;

        private StubHandler(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void execute(AsyncTask task) {
        }
    }
}
