package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UserSseRegistryTest {

    @Test
    void pushIsSilentWhenUserNotRegistered() {
        UserSseRegistry registry = new UserSseRegistry();

        assertDoesNotThrow(() -> registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L)));
    }

    @Test
    void registerImmediatelyCommitsSseConnection() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter emitter = new CountingEmitter();

        registry.register(1L, emitter);

        assertEquals(1, emitter.sendCount);
    }

    @Test
    void heartbeatKeepsRegisteredConnectionActive() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter emitter = new CountingEmitter();
        registry.register(1L, emitter);

        registry.sendHeartbeat();

        assertEquals(2, emitter.sendCount);
    }

    @Test
    void removeDoesNotThrowWhenUserNotRegistered() {
        UserSseRegistry registry = new UserSseRegistry();

        assertDoesNotThrow(() -> registry.remove(99L));
    }

    @Test
    void removesEmitterAfterRegisterAndRemove() {
        UserSseRegistry registry = new UserSseRegistry();

        registry.register(1L, new SseEmitter(0L));
        registry.remove(1L);

        assertDoesNotThrow(() -> registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L)));
    }

    @Test
    void runtimeSendFailureDoesNotEscapeAndRemovesEmitter() {
        UserSseRegistry registry = new UserSseRegistry();
        IllegalStateEmitter failingEmitter = new IllegalStateEmitter();

        registry.register(1L, failingEmitter);

        assertDoesNotThrow(() -> registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L)));
        assertDoesNotThrow(() -> registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L)));

        assertEquals(1, failingEmitter.sendCount);
    }

    @Test
    void failedOldEmitterDoesNotRemoveNewEmitterRegisteredDuringSend() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter newEmitter = new CountingEmitter();

        registry.register(1L, new ReplacingFailingEmitter(registry, 1L, newEmitter));

        assertDoesNotThrow(() -> registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L)));
        registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));

        assertEquals(2, newEmitter.sendCount);
    }

    @Test
    void registerKeepsMultipleEmittersForSameUser() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter firstEmitter = new CountingEmitter();
        CountingEmitter secondEmitter = new CountingEmitter();

        registry.register(1L, firstEmitter);
        registry.register(1L, secondEmitter);
        firstEmitter.reset();
        secondEmitter.reset();
        registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));

        assertEquals(1, firstEmitter.sendCount);
        assertEquals(1, secondEmitter.sendCount);
    }

    @Test
    void staleRemoveDoesNotRemoveNewEmitterRegisteredForSameUser() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter oldEmitter = new CountingEmitter();
        CountingEmitter newEmitter = new CountingEmitter();

        registry.register(1L, oldEmitter);
        registry.register(1L, newEmitter);
        registry.remove(1L, oldEmitter);
        newEmitter.reset();

        registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));

        assertEquals(1, newEmitter.sendCount);
    }

    @Test
    void pushAndCloseSendsToEveryEmitterThenClosesAndRemovesThem() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter firstEmitter = new CountingEmitter();
        CountingEmitter secondEmitter = new CountingEmitter();
        registry.register(1L, firstEmitter);
        registry.register(1L, secondEmitter);
        firstEmitter.reset();
        secondEmitter.reset();

        registry.pushAndClose(1L, "account-invalidated", Map.of("reason", "BANNED"));

        assertEquals(1, firstEmitter.sendCount);
        assertEquals(1, secondEmitter.sendCount);
        assertEquals(1, firstEmitter.completeCount);
        assertEquals(1, secondEmitter.completeCount);

        registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));
        assertEquals(1, firstEmitter.sendCount);
        assertEquals(1, secondEmitter.sendCount);
    }

    @Test
    void pushAndCloseClosesFailingFirstEmitterAndContinuesWithLaterEmitter() {
        UserSseRegistry registry = new UserSseRegistry();
        List<String> sendOrder = new ArrayList<>();
        FinalSendFailingEmitter failingEmitter = new FinalSendFailingEmitter(sendOrder);
        CountingEmitter laterEmitter = new LaterEmitter(sendOrder);
        registry.register(1L, failingEmitter);
        registry.register(1L, laterEmitter);
        failingEmitter.reset();
        laterEmitter.reset();
        sendOrder.clear();

        registry.pushAndClose(1L, "account-invalidated", Map.of("reason", "BANNED"));

        assertEquals(List.of("failing", "later"), sendOrder);
        assertEquals(1, failingEmitter.sendCount);
        assertEquals(1, failingEmitter.completeCount);
        assertEquals(1, laterEmitter.sendCount);
        assertEquals(1, laterEmitter.completeCount);
    }

    @Test
    void targetedPushAndCloseClosesOnlyOldEmitterAndKeepsNewEmitterRegistered() {
        UserSseRegistry registry = new UserSseRegistry();
        CountingEmitter oldEmitter = new CountingEmitter();
        CountingEmitter newEmitter = new CountingEmitter();
        registry.register(1L, oldEmitter);
        registry.register(1L, newEmitter);
        oldEmitter.reset();
        newEmitter.reset();

        registry.pushAndClose(
            1L,
            oldEmitter,
            "account-invalidated",
            Map.of("reason", "SESSION_EXPIRED")
        );

        assertEquals(1, oldEmitter.sendCount);
        assertEquals(1, oldEmitter.completeCount);
        assertEquals(0, newEmitter.sendCount);
        assertEquals(0, newEmitter.completeCount);

        registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));
        assertEquals(1, oldEmitter.sendCount);
        assertEquals(1, newEmitter.sendCount);
    }

    @Test
    void targetedPushAndCloseCannotDetachConcurrentlyRegisteredEmitter() throws Exception {
        UserSseRegistry registry = new UserSseRegistry();
        CoordinatedStore coordinatedStore = new CoordinatedStore();
        replaceStore(registry, coordinatedStore);
        CountingEmitter oldEmitter = new CountingEmitter();
        CountingEmitter newEmitter = new CountingEmitter();
        registry.register(1L, oldEmitter);
        oldEmitter.reset();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> registration = executor.submit(() -> {
                coordinatedStore.markRegistrationThread();
                registry.register(1L, newEmitter);
            });
            assertTrue(coordinatedStore.awaitRegistrationUpdate());

            registry.pushAndClose(
                1L,
                oldEmitter,
                "account-invalidated",
                Map.of("reason", "SESSION_EXPIRED")
            );
            registration.get(1, TimeUnit.SECONDS);

            assertEquals(1, oldEmitter.sendCount);
            assertEquals(1, oldEmitter.completeCount);
            assertEquals(1, newEmitter.sendCount);
            assertEquals(0, newEmitter.completeCount);

            newEmitter.reset();
            registry.push(1L, "ai-cache-ready", Map.of("cardId", 1L));
            assertEquals(1, newEmitter.sendCount);
        } finally {
            coordinatedStore.releaseRegistration();
            executor.shutdownNow();
        }
    }

    private static void replaceStore(UserSseRegistry registry, Map<Long, Set<SseEmitter>> store)
        throws ReflectiveOperationException {
        Field field = UserSseRegistry.class.getDeclaredField("store");
        field.setAccessible(true);
        field.set(registry, store);
    }

    private static class CoordinatedStore extends ConcurrentHashMap<Long, Set<SseEmitter>> {

        private final CountDownLatch registrationUpdate = new CountDownLatch(1);
        private final CountDownLatch targetedDetach = new CountDownLatch(1);
        private volatile Thread registrationThread;

        void markRegistrationThread() {
            registrationThread = Thread.currentThread();
        }

        boolean awaitRegistrationUpdate() throws InterruptedException {
            return registrationUpdate.await(1, TimeUnit.SECONDS);
        }

        void releaseRegistration() {
            targetedDetach.countDown();
        }

        @Override
        public Set<SseEmitter> computeIfAbsent(
            Long key,
            Function<? super Long, ? extends Set<SseEmitter>> mappingFunction
        ) {
            Set<SseEmitter> emitters = super.computeIfAbsent(key, mappingFunction);
            if (Thread.currentThread() == registrationThread) {
                registrationUpdate.countDown();
                await(targetedDetach);
            }
            return emitters;
        }

        @Override
        public Set<SseEmitter> compute(
            Long key,
            BiFunction<? super Long, ? super Set<SseEmitter>, ? extends Set<SseEmitter>> remappingFunction
        ) {
            Set<SseEmitter> emitters = super.compute(key, remappingFunction);
            if (Thread.currentThread() == registrationThread) {
                registrationUpdate.countDown();
            }
            return emitters;
        }

        @Override
        public boolean remove(Object key, Object value) {
            boolean removed = super.remove(key, value);
            if (removed) {
                targetedDetach.countDown();
            }
            return removed;
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for targeted detach");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for targeted detach", ex);
            }
        }
    }

    private static class IllegalStateEmitter extends SseEmitter {

        private int sendCount;

        IllegalStateEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            throw new IllegalStateException("emitter closed");
        }
    }

    private static class ReplacingFailingEmitter extends SseEmitter {

        private final UserSseRegistry registry;
        private final Long userId;
        private final SseEmitter newEmitter;
        private int sendCount;

        ReplacingFailingEmitter(UserSseRegistry registry, Long userId, SseEmitter newEmitter) {
            super(0L);
            this.registry = registry;
            this.userId = userId;
            this.newEmitter = newEmitter;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            if (sendCount == 1) {
                return;
            }
            registry.register(userId, newEmitter);
            throw new IllegalStateException("old emitter closed");
        }
    }

    private static class FinalSendFailingEmitter extends CountingEmitter {

        private final List<String> sendOrder;
        private boolean failNextSend;

        FinalSendFailingEmitter(List<String> sendOrder) {
            this.sendOrder = sendOrder;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            super.send(builder);
            if (failNextSend) {
                failNextSend = false;
                sendOrder.add("failing");
                throw new IOException("final send failed");
            }
        }

        @Override
        void reset() {
            super.reset();
            failNextSend = true;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static class LaterEmitter extends CountingEmitter {

        private final List<String> sendOrder;

        LaterEmitter(List<String> sendOrder) {
            this.sendOrder = sendOrder;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            super.send(builder);
            sendOrder.add("later");
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static class CountingEmitter extends SseEmitter {

        protected int sendCount;
        protected int completeCount;

        CountingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
        }

        @Override
        public void complete() {
            completeCount++;
        }

        void reset() {
            sendCount = 0;
        }
    }

}
