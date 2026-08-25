package openflash_core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserAccountInvalidationNotifier;
import openflash_core.service.UserSseRegistry;
import openflash_core.spi.UserAccountInvalidatedEvent;
import openflash_core.spi.UserAccountInvalidatedEvent.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseControllerTest {

    private AnnotationConfigApplicationContext context;
    private RecordingRegistry registry;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        registry = context.getBean(RecordingRegistry.class);
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void bannedInvalidationAfterValidationStillClosesEmitterRegisteredByOldSession() {
        assertInvalidationRace(Reason.BANNED, ErrorCode.ACCOUNT_BANNED);
    }

    @Test
    void deletedInvalidationAfterValidationStillClosesEmitterRegisteredByOldSession() {
        assertInvalidationRace(Reason.DELETED, ErrorCode.ACCOUNT_DELETED);
    }

    private void assertInvalidationRace(Reason reason, ErrorCode errorCode) {
        List<String> interleaving = registry.interleaving;
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        AtomicInteger validations = new AtomicInteger();
        when(currentUserService.getCurrentUserId()).thenAnswer(invocation -> {
            if (validations.getAndIncrement() == 0) {
                interleaving.add("validated");
                transactionTemplate.executeWithoutResult(status ->
                    context.publishEvent(new UserAccountInvalidatedEvent(8L, reason))
                );
                return 8L;
            }
            interleaving.add("revalidated:" + errorCode.name());
            throw new AppException(errorCode);
        });

        SseEmitter emitter = new SseController(registry, currentUserService).notifications();

        assertSame(emitter, registry.registeredEmitter);
        assertSame(emitter, registry.closedEmitter);
        assertEquals(
            Map.of("reason", reason.name(), "code", errorCode.value()),
            registry.finalPayload
        );
        assertEquals(
            List.of("validated", "after-commit", "registered", "revalidated:" + errorCode.name(), "closed"),
            interleaving
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        RecordingRegistry userSseRegistry() {
            return new RecordingRegistry();
        }

        @Bean
        UserAccountInvalidationNotifier userAccountInvalidationNotifier(UserSseRegistry registry) {
            return new UserAccountInvalidationNotifier(registry);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new StubTransactionManager();
        }
    }

    private static class RecordingRegistry extends UserSseRegistry {

        private final List<String> interleaving = new ArrayList<>();
        private SseEmitter registeredEmitter;
        private SseEmitter closedEmitter;
        private Object finalPayload;

        @Override
        public void register(Long userId, SseEmitter emitter) {
            interleaving.add("registered");
            registeredEmitter = emitter;
            super.register(userId, emitter);
        }

        @Override
        public void pushAndClose(Long userId, String eventName, Object payload) {
            interleaving.add("after-commit");
            super.pushAndClose(userId, eventName, payload);
        }

        @Override
        public void pushAndClose(
            Long userId,
            SseEmitter emitter,
            String eventName,
            Object payload
        ) {
            interleaving.add("closed");
            closedEmitter = emitter;
            finalPayload = payload;
            super.pushAndClose(userId, emitter, eventName, payload);
        }
    }

    private static class StubTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
