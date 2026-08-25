package openflash_plugin.tts.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import openflash_core.entity.AsyncTask;

class TtsTaskExecutorTest {

    @Test
    void executeIgnoresLegacyPrewarmTaskPayload() {
        AsyncTask task = new AsyncTask();
        task.setPayload("{not-json");

        assertDoesNotThrow(() -> new TtsTaskExecutor(enabledGuard()).execute(task));
    }

    @Test
    void executeSkipsWhenTtsPluginIsOff() {
        AsyncTask task = new AsyncTask();
        task.setPayload("{not-json");
        TtsFeatureGuard guard = mock(TtsFeatureGuard.class);
        when(guard.isTtsEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> new TtsTaskExecutor(guard).execute(task));
    }

    /**
     * 创建测试用开启 guard，让旧预热任务按原有空执行路径完成。
     */
    private TtsFeatureGuard enabledGuard() {
        TtsFeatureGuard guard = mock(TtsFeatureGuard.class);
        when(guard.isTtsEnabled()).thenReturn(true);
        return guard;
    }
}
