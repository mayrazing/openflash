package openflash_plugin.ai_card.controller;

import openflash_plugin.ai_card.service.CardAiService;
import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;

class AiCardControllerTest {

    @Test
    void featureStateReturnsSideCompletionDisabled() throws Exception {
        AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
        when(featureGuard.isSideCompletionEnabled()).thenReturn(false);

        mvc(mock(CardAiService.class), featureGuard)
            .perform(get("/api/plugins/ai-card/features"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sideCompletionEnabled").value(false));
    }

    @Test
    void cacheStatusReturnsFeatureDisabledWhenAiPluginIsOff() throws Exception {
        AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(featureGuard).ensureAiCardEnabled();

        mvc(mock(CardAiService.class), featureGuard)
            .perform(get("/api/cards/9/ai-cache-status"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void regenerateCallsServiceWithSide() throws Exception {
        AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
        CardAiService cardAiService = mock(CardAiService.class);
        when(cardAiService.regenerateAiCache(9L, "B"))
                .thenReturn(openflash_plugin.ai_card.dto.AiCacheStatusResponse.queued());

        mvc(cardAiService, featureGuard)
            .perform(post("/api/cards/9/ai-cache-regenerate?side=B"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("queued"));

        verify(cardAiService).regenerateAiCache(9L, "B");
    }

    /** 创建只加载 AI 卡片控制器和全局异常处理器的测试入口。 */
    private static MockMvc mvc(CardAiService cardAiService, AiCardFeatureGuard featureGuard) {
        return MockMvcBuilders
            .standaloneSetup(new AiCardController(cardAiService, featureGuard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
