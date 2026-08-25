package openflash_plugin.mask_mode.controller;

import openflash_plugin.mask_mode.dto.MaskModeDeckSettingsUpdateCommand;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;
import openflash_plugin.mask_mode.service.MaskModeDeckSettingsService;
import openflash_plugin.mask_mode.service.impl.MaskModeFeatureGuard;

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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;

class MaskModeDeckSettingsControllerTest {

    @Test
    void getOwnedDeckReturnsDeckIdAndMode() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        when(service.getForCurrentUser(11L)).thenReturn(new MaskModeDeckSettings(11L, "full", true));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/plugins/mask-mode/decks/11/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deckId").value(11))
            .andExpect(jsonPath("$.data.mode").value("full"))
            .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void getOwnedDeckExposesEnabledFalseWhenSwitchOff() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        when(service.getForCurrentUser(11L)).thenReturn(new MaskModeDeckSettings(11L, "random", false));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/plugins/mask-mode/decks/11/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void putOwnedDeckPassesModeAndEnabledToService() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        when(service.saveForCurrentUser(eq(11L), any(MaskModeDeckSettingsUpdateCommand.class)))
            .thenReturn(new MaskModeDeckSettings(11L, "full", false));
        MockMvc mvc = mvc(service);

        mvc.perform(put("/api/plugins/mask-mode/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "full",
                      "enabled": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deckId").value(11))
            .andExpect(jsonPath("$.data.mode").value("full"))
            .andExpect(jsonPath("$.data.enabled").value(false));

        ArgumentCaptor<MaskModeDeckSettingsUpdateCommand> captor =
            ArgumentCaptor.forClass(MaskModeDeckSettingsUpdateCommand.class);
        verify(service).saveForCurrentUser(eq(11L), captor.capture());
        Assertions.assertEquals("full", captor.getValue().mode());
        Assertions.assertEquals(Boolean.FALSE, captor.getValue().enabled());
    }

    @Test
    void putOwnedDeckPassesEnabledTrue() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        when(service.saveForCurrentUser(eq(11L), any(MaskModeDeckSettingsUpdateCommand.class)))
            .thenReturn(new MaskModeDeckSettings(11L, "random", true));
        MockMvc mvc = mvc(service);

        mvc.perform(put("/api/plugins/mask-mode/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "random",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true));

        ArgumentCaptor<MaskModeDeckSettingsUpdateCommand> captor =
            ArgumentCaptor.forClass(MaskModeDeckSettingsUpdateCommand.class);
        verify(service).saveForCurrentUser(eq(11L), captor.capture());
        Assertions.assertEquals(Boolean.TRUE, captor.getValue().enabled());
    }

    @Test
    void getSettingsReturnsFeatureDisabledWhenMaskModePluginIsOff() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        MaskModeFeatureGuard guard = mock(MaskModeFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(guard).ensureMaskModeEnabled();
        MockMvc mvc = mvc(service, guard);

        mvc.perform(get("/api/plugins/mask-mode/decks/11/settings"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void putSettingsReturnsFeatureDisabledWhenMaskModePluginIsOff() throws Exception {
        MaskModeDeckSettingsService service = mock(MaskModeDeckSettingsService.class);
        MaskModeFeatureGuard guard = mock(MaskModeFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(guard).ensureMaskModeEnabled();
        MockMvc mvc = mvc(service, guard);

        mvc.perform(put("/api/plugins/mask-mode/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "random"
                    }
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    /**
     * 创建只加载目标控制器和全局异常处理器的测试入口。
     */
    private static MockMvc mvc(MaskModeDeckSettingsService service) {
        return mvc(service, mock(MaskModeFeatureGuard.class));
    }

    /**
     * 创建只加载目标控制器、guard 和全局异常处理器的测试入口。
     */
    private static MockMvc mvc(MaskModeDeckSettingsService service, MaskModeFeatureGuard guard) {
        return MockMvcBuilders
            .standaloneSetup(new MaskModeDeckSettingsController(service, guard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
