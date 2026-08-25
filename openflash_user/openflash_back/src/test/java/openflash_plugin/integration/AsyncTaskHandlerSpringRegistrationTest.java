package openflash_plugin.integration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;
import openflash_plugin.ai_card.service.impl.AiTaskExecutor;
import openflash_plugin.ai_card.service.impl.CardAiGenerationCore;
import openflash_plugin.ai_card.service.impl.CardAiCacheTaskProducer;
import openflash_core.service.AsyncTaskHandler;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_plugin.tts.service.impl.TtsFeatureGuard;
import openflash_plugin.tts.service.TtsService;
import openflash_plugin.tts.service.impl.TtsTaskExecutor;
import openflash_core.service.UserSseRegistry;
import openflash_core.service.impl.AsyncTaskHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

@SpringJUnitConfig(AsyncTaskHandlerSpringRegistrationTest.TestConfig.class)
class AsyncTaskHandlerSpringRegistrationTest {

    @Autowired
    private AsyncTaskHandlerRegistry registry;

    @Test
    void springRegistersAiAndTtsHandlers() {
        assertInstanceOf(AiTaskExecutor.class, registry.getRequired(CardAiCacheTaskProducer.TASK_TYPE));
        assertInstanceOf(TtsTaskExecutor.class, registry.getRequired(TtsTaskExecutor.TASK_TYPE));
    }

    @Configuration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CardAiCacheService cardAiCacheService() {
            return new FakeCardAiCacheService();
        }

        @Bean
        CardAiGenerationCore cardAiGenerationCore() {
            return new FakeCardAiGenerationCore();
        }

        @Bean
        TtsService ttsService() {
            return new FakeTtsService();
        }

        @Bean
        AiTaskExecutor aiTaskExecutor(CardAiCacheService cardAiCacheService, CardAiGenerationCore cardAiGenerationCore, ObjectMapper objectMapper) {
            return new AiTaskExecutor(cardAiCacheService, cardAiGenerationCore, objectMapper, new UserSseRegistry(), mockFeatureGuard());
        }

        @Bean
        TtsTaskExecutor ttsTaskExecutor() {
            return new TtsTaskExecutor(mockTtsFeatureGuard());
        }

        @Bean
        AsyncTaskHandlerRegistry asyncTaskHandlerRegistry(List<AsyncTaskHandler> handlers) {
            return new AsyncTaskHandlerRegistry(handlers);
        }
    }

    private static final class FakeCardAiCacheService implements CardAiCacheService {
        @Override
        public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
            return null;
        }

        @Override
        public void saveReadyFromBackground(Long ownerUserId, String fingerprint, String prompt, String content, Boolean thinkUsed) {
        }
    }

    private static final class FakeCardAiGenerationCore extends CardAiGenerationCore {
        private FakeCardAiGenerationCore() {
            super(null, null, null, mockFeatureGuard(),
                new openflash_core.service.impl.EffectiveAiProfileResolver(null));
        }
    }

    /**
     * 创建默认开启的 ai-card guard，避免测试替身触发关闭分支。
     */
    private static AiCardFeatureGuard mockFeatureGuard() {
        AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
        when(featureGuard.isAiCardEnabled()).thenReturn(true);
        return featureGuard;
    }

    /**
     * 创建默认开启的 TTS guard，避免测试替身触发关闭分支。
     */
    private static TtsFeatureGuard mockTtsFeatureGuard() {
        TtsFeatureGuard featureGuard = mock(TtsFeatureGuard.class);
        when(featureGuard.isTtsEnabled()).thenReturn(true);
        return featureGuard;
    }

    private static final class FakeTtsService implements TtsService {
    }
}
