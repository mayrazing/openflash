package openflash_plugin.ai_card.controller;

import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand;

class DeckAiSettingsControllerTest {

    @Test
    void getOwnedDeckReturnsDefaultAiSettings() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.getForCurrentUser(11L)).thenReturn(defaultSettings(11L));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(get("/api/decks/11/ai-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deckId").value(11))
                .andExpect(jsonPath("$.data.aiExplanationEnabledA").value(false))
                .andExpect(jsonPath("$.data.aiExplanationEnabledB").value(false))
                .andExpect(jsonPath("$.data.aiCompletionEnabled").value(false));
    }

    @Test
    void getNotOwnedDeckReturnsDeckNotFound() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.getForCurrentUser(11L)).thenThrow(new AppException(ErrorCode.DECK_NOT_FOUND));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(get("/api/decks/11/ai-settings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.DECK_NOT_FOUND.value()));
    }

    @Test
    void getAiSettingsReturnsFeatureDisabledWhenAiPluginIsOff() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(featureGuard).ensureAiCardEnabled();
        MockMvc mvc = mvc(deckAiSettingsService, featureGuard);

        mvc.perform(get("/api/decks/11/ai-settings"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void putOwnedDeckPassesFullPayloadToService() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class)))
                .thenReturn(savedSettings(11L));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(put("/api/decks/11/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "aiExplanationEnabledA": false,
                          "aiExplanationEnabledB": true,
                          "aiExplanationPromptA": "explain A",
                          "aiExplanationPromptB": "explain B",
                          "aiCompletionEnabled": true,
                          "aiCompletionPrompt": "complete side"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deckId").value(11))
                .andExpect(jsonPath("$.data.aiExplanationPromptA").value("explain A"))
                .andExpect(jsonPath("$.data.aiExplanationPromptB").value("explain B"))
                .andExpect(jsonPath("$.data.aiCompletionEnabled").value(true));

        ArgumentCaptor<DeckAiSettingsUpdateCommand> captor = ArgumentCaptor.forClass(DeckAiSettingsUpdateCommand.class);
        verify(deckAiSettingsService).saveForCurrentUser(eq(11L), captor.capture());
        DeckAiSettingsUpdateCommand command = captor.getValue();
        Assertions.assertEquals(false, command.aiExplanationEnabledA());
        Assertions.assertEquals(true, command.aiExplanationEnabledB());
        Assertions.assertEquals("explain A", command.aiExplanationPromptA());
        Assertions.assertEquals("explain B", command.aiExplanationPromptB());
        Assertions.assertEquals(true, command.aiCompletionEnabled());
        Assertions.assertEquals("complete side", command.aiCompletionPrompt());
    }

    @Test
    void putNotOwnedDeckDoesNotCallSaveAndReturnsDeckNotFound() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class)))
                .thenThrow(new AppException(ErrorCode.DECK_NOT_FOUND));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(put("/api/decks/11/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "aiExplanationEnabledA": true,
                          "aiExplanationEnabledB": true,
                          "aiExplanationPromptA": "explain",
                          "aiExplanationPromptB": "explain",
                          "aiCompletionEnabled": true,
                          "aiCompletionPrompt": "complete side"
                        }
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.DECK_NOT_FOUND.value()));

        verify(deckAiSettingsService).saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class));
    }

    @Test
    void putInvalidPayloadReturnsDeckSettingsInvalid() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class)))
                .thenThrow(new AppException(ErrorCode.DECK_SETTINGS_INVALID));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(put("/api/decks/11/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "aiExplanationEnabledA": true,
                          "aiExplanationEnabledB": true,
                          "aiExplanationPromptA": "explain",
                          "aiExplanationPromptB": "explain",
                          "aiCompletionEnabled": true,
                          "aiCompletionPrompt": "complete side"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.DECK_SETTINGS_INVALID.value()));
    }

    @Test
    void putNullPayloadReturnsDeckSettingsInvalidAndDoesNotCallSave() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(put("/api/decks/11/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.DECK_SETTINGS_INVALID.value()));

        verify(deckAiSettingsService, never()).saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class));
    }

    @Test
    void getOwnedDeckReturnsAiEnabledAAndB() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        DeckAiSettings settings = new DeckAiSettings();
        settings.setDeckId(11L);
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(false);
        settings.setAiCompletionEnabled(true);
        when(deckAiSettingsService.getForCurrentUser(11L)).thenReturn(settings);
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(get("/api/decks/11/ai-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExplanationEnabledA").value(true))
                .andExpect(jsonPath("$.data.aiExplanationEnabledB").value(false));
    }

    @Test
    void putOwnedDeckPassesAiEnabledAAndBToService() throws Exception {
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        when(deckAiSettingsService.saveForCurrentUser(eq(11L), any(DeckAiSettingsUpdateCommand.class)))
                .thenReturn(savedSettings(11L));
        MockMvc mvc = mvc(deckAiSettingsService);

        mvc.perform(put("/api/decks/11/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "aiExplanationEnabledA": true,
                          "aiExplanationEnabledB": false,
                          "aiExplanationPromptA": "explain A",
                          "aiExplanationPromptB": "explain B",
                          "aiCompletionEnabled": true,
                          "aiCompletionPrompt": "complete side"
                        }
                        """))
                .andExpect(status().isOk());

        ArgumentCaptor<DeckAiSettingsUpdateCommand> captor = ArgumentCaptor.forClass(DeckAiSettingsUpdateCommand.class);
        verify(deckAiSettingsService).saveForCurrentUser(eq(11L), captor.capture());
        DeckAiSettingsUpdateCommand command = captor.getValue();
        Assertions.assertEquals(true, command.aiExplanationEnabledA());
        Assertions.assertEquals(false, command.aiExplanationEnabledB());
    }

    /**
     * 创建只加载目标控制器和全局异常处理器的测试入口。
     */
    private static MockMvc mvc(DeckAiSettingsService deckAiSettingsService) {
        return mvc(deckAiSettingsService, mock(AiCardFeatureGuard.class));
    }

    /**
     * 创建只加载目标控制器、guard 和全局异常处理器的测试入口。
     */
    private static MockMvc mvc(DeckAiSettingsService deckAiSettingsService, AiCardFeatureGuard featureGuard) {
        return MockMvcBuilders
                .standaloneSetup(new DeckAiSettingsController(deckAiSettingsService, featureGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 创建用户初次进入页面时看到的默认 AI 设置。
     */
    private static DeckAiSettings defaultSettings(Long deckId) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setDeckId(deckId);
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(false);
        settings.setAiCompletionEnabled(false);
        return settings;
    }

    /**
     * 创建保存后服务层返回给页面的 AI 设置。
     */
    private static DeckAiSettings savedSettings(Long deckId) {
        DeckAiSettings settings = defaultSettings(deckId);
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(true);
        settings.setAiExplanationPromptA("explain A");
        settings.setAiExplanationPromptB("explain B");
        settings.setAiCompletionEnabled(true);
        settings.setAiCompletionPrompt("complete side");
        return settings;
    }
}
