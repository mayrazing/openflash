package openflash_plugin.tts.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_plugin.tts.common.TtsErrorCode;

class TtsRequestAdmissionGuardTest {

    @Test
    void rejectsRequestBeyondPerUserLimitAndReleasesRegistryEntry() throws Exception {
        TtsRequestAdmissionGuard guard = new TtsRequestAdmissionGuard(3, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                guard.call(7L, () -> {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    return null;
                });
            } catch (Throwable ex) {
                firstFailure.set(ex);
            }
        });

        first.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        AppException error = assertThrows(AppException.class, () -> guard.call(7L, () -> null));
        assertEquals(TtsErrorCode.TTS_BUSY, error.getErrorCode());

        release.countDown();
        first.join(2000);
        assertNull(firstFailure.get());
        assertEquals(0, guard.trackedUserCountForTest());
    }
}
