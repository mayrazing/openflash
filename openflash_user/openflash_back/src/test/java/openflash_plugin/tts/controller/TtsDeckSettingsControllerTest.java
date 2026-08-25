package openflash_plugin.tts.controller;

import openflash_plugin.tts.dto.TtsDeckSettingsUpdateCommand;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.service.TtsDeckSettingsService;
import openflash_plugin.tts.service.impl.TtsFeatureGuard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;

class TtsDeckSettingsControllerTest {

    @Test
    void getOwnedDeckReturnsAutoSpeakAndDefaultModel() throws Exception {
        TtsDeckSettingsService service = mock(TtsDeckSettingsService.class);
        when(service.getForCurrentUser(11L))
            .thenReturn(new TtsDeckSettings(11L, true, false, "piper"));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/plugins/tts/decks/11/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deckId").value(11))
            .andExpect(jsonPath("$.data.autoSpeakA").value(true))
            .andExpect(jsonPath("$.data.autoSpeakB").value(false))
            .andExpect(jsonPath("$.data.engine").value("piper"));
    }

    @Test
    void putOwnedDeckPassesDefaultModelToService() throws Exception {
        TtsDeckSettingsService service = mock(TtsDeckSettingsService.class);
        when(service.saveForCurrentUser(eq(11L), any(TtsDeckSettingsUpdateCommand.class)))
            .thenReturn(new TtsDeckSettings(11L, false, true, "cosyvoice3"));
        MockMvc mvc = mvc(service);

        mvc.perform(put("/api/plugins/tts/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "autoSpeakA": false,
                      "autoSpeakB": true,
                      "engine": "cosyvoice3"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.engine").value("cosyvoice3"));

        ArgumentCaptor<TtsDeckSettingsUpdateCommand> captor =
            ArgumentCaptor.forClass(TtsDeckSettingsUpdateCommand.class);
        verify(service).saveForCurrentUser(eq(11L), captor.capture());
        Assertions.assertEquals(false, captor.getValue().autoSpeakA());
        Assertions.assertEquals(true, captor.getValue().autoSpeakB());
        Assertions.assertEquals("cosyvoice3", captor.getValue().engine());
    }

    @Test
    void listModelsReturnsOnlyServiceApprovedRegistryKeys() throws Exception {
        TtsDeckSettingsService service = mock(TtsDeckSettingsService.class);
        when(service.getEnabledEngines()).thenReturn(List.of("cosyvoice3", "piper"));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/plugins/tts/engines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("cosyvoice3"))
            .andExpect(jsonPath("$.data[1]").value("piper"));
    }

    @Test
    void getSettingsReturnsFeatureDisabledWhenUnifiedPluginIsOff() throws Exception {
        TtsDeckSettingsService service = mock(TtsDeckSettingsService.class);
        TtsFeatureGuard guard = mock(TtsFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(guard).ensureTtsEnabled();
        MockMvc mvc = mvc(service, guard);

        mvc.perform(get("/api/plugins/tts/decks/11/settings"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    private static MockMvc mvc(TtsDeckSettingsService service) {
        return mvc(service, mock(TtsFeatureGuard.class));
    }

    private static MockMvc mvc(TtsDeckSettingsService service, TtsFeatureGuard guard) {
        return MockMvcBuilders
            .standaloneSetup(new TtsDeckSettingsController(service, guard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
