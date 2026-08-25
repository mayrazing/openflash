package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

class UserAccountInvalidationNotifierTest {

    private AnnotationConfigApplicationContext context;
    private RecordingUserSseRegistry registry;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        registry = context.getBean(RecordingUserSseRegistry.class);
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void committedTransactionPushesInvalidationOnlyAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(new UserAccountInvalidatedEvent(8L, Reason.BANNED));
            assertEquals(List.of(), registry.notifications);
        });

        assertEquals(
            List.of(new Notification(
                8L,
                "account-invalidated",
                Map.of("reason", "BANNED", "code", 40103)
            )),
            registry.notifications
        );
    }

    @Test
    void rolledBackTransactionDoesNotPushInvalidation() {
        transactionTemplate.executeWithoutResult(status -> {
            context.publishEvent(new UserAccountInvalidatedEvent(8L, Reason.DELETED));
            status.setRollbackOnly();
        });

        assertEquals(List.of(), registry.notifications);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        RecordingUserSseRegistry userSseRegistry() {
            return new RecordingUserSseRegistry();
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

    private record Notification(Long userId, String eventName, Object payload) {
    }

    static class RecordingUserSseRegistry extends UserSseRegistry {

        private final List<Notification> notifications = new ArrayList<>();

        @Override
        public void pushAndClose(Long userId, String eventName, Object payload) {
            notifications.add(new Notification(userId, eventName, payload));
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
