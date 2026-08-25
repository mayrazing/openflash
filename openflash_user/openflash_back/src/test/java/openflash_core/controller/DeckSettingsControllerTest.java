package openflash_core.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.common.GlobalExceptionHandler;
import openflash_core.entity.DeckSettings;
import openflash_core.dto.DeckSettingsUpdateCommand;
import openflash_core.entity.PracticeModeOption;
import openflash_core.service.DeckSettingsService;
import openflash_core.service.TypeRegistryService;

class DeckSettingsControllerTest {

    @Test
    void getDeckSettingsReturnsCoreDeckScopedFields() throws Exception {
        DeckSettingsService deckSettingsService = mock(DeckSettingsService.class);
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(deckSettingsService.getSettings(11L)).thenReturn(settings(11L));
        MockMvc mvc = mvc(deckSettingsService, typeRegistryService);

        mvc.perform(get("/api/decks/11/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deckId").value(11))
            .andExpect(jsonPath("$.data.newCardsPerDay").value(12))
            .andExpect(jsonPath("$.data.targetRetention").value(0.86))
            .andExpect(jsonPath("$.data.reviewLoadProfile").value("relaxed"))
            .andExpect(jsonPath("$.data.duplicateSideAEnabled").value(false))
            .andExpect(jsonPath("$.data.duplicateSideBEnabled").value(true));
    }

    @Test
    void putDeckSettingsPassesFullPayloadToService() throws Exception {
        DeckSettingsService deckSettingsService = mock(DeckSettingsService.class);
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(deckSettingsService.updateSettings(eq(11L), any(DeckSettingsUpdateCommand.class))).thenReturn(settings(11L));
        MockMvc mvc = mvc(deckSettingsService, typeRegistryService);

        mvc.perform(put("/api/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "newCardsPerDay": 20,
                      "targetRetention": 0.91,
                      "reviewLoadProfile": "intensive",
                      "duplicateSideAEnabled": true,
                      "duplicateSideBEnabled": false
                    }
                    """))
            .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(DeckSettingsUpdateCommand.class);
        verify(deckSettingsService).updateSettings(eq(11L), captor.capture());
        DeckSettingsUpdateCommand command = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(20, command.newCardsPerDay());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("0.91"), command.targetRetention());
        org.junit.jupiter.api.Assertions.assertEquals("intensive", command.reviewLoadProfile());
        org.junit.jupiter.api.Assertions.assertEquals(true, command.duplicateSideAEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(false, command.duplicateSideBEnabled());
    }

    @Test
    void getDeckSettingsReturns404WhenDeckNotOwned() throws Exception {
        DeckSettingsService deckSettingsService = mock(DeckSettingsService.class);
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(deckSettingsService.getSettings(11L)).thenThrow(new AppException(ErrorCode.DECK_NOT_FOUND));
        MockMvc mvc = mvc(deckSettingsService, typeRegistryService);

        mvc.perform(get("/api/decks/11/settings"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.DECK_NOT_FOUND.value()));
    }

    @Test
    void putDeckSettingsReturns40032WhenPayloadIncomplete() throws Exception {
        DeckSettingsService deckSettingsService = mock(DeckSettingsService.class);
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(deckSettingsService.updateSettings(eq(11L), any(DeckSettingsUpdateCommand.class)))
            .thenThrow(new AppException(ErrorCode.DECK_SETTINGS_INVALID));
        MockMvc mvc = mvc(deckSettingsService, typeRegistryService);

        mvc.perform(put("/api/decks/11/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "newCardsPerDay": 20,
                      "targetRetention": 0.91,
                      "reviewLoadProfile": "intensive",
                      "duplicateSideAEnabled": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.DECK_SETTINGS_INVALID.value()));
    }

    @Test
    void reviewLoadProfilesReturnsFromDeckSettingsNamespace() throws Exception {
        TypeRegistryService typeRegistryService = mock(TypeRegistryService.class);
        when(typeRegistryService.getEnabledReviewLoadProfiles()).thenReturn(List.of(
            new PracticeModeOption("relaxed", "轻松"),
            new PracticeModeOption("standard", "标准")
        ));
        MockMvc mvc = mvc(mock(DeckSettingsService.class), typeRegistryService);

        mvc.perform(get("/api/deck-settings/review-load-profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("relaxed"))
            .andExpect(jsonPath("$.data[1].value").value("standard"));
    }

    /**
     * 创建只加载目标控制器和全局异常处理器的测试入口。
     */
    private static MockMvc mvc(DeckSettingsService deckSettingsService, TypeRegistryService typeRegistryService) {
        return MockMvcBuilders
            .standaloneSetup(new DeckSettingsController(deckSettingsService, typeRegistryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    /**
     * 创建核心卡包设置响应用实体。
     */
    private static DeckSettings settings(Long deckId) {
        DeckSettings settings = new DeckSettings();
        settings.setDeckId(deckId);
        settings.setNewCardsPerDay(12);
        settings.setTargetRetention(new BigDecimal("0.8600"));
        settings.setReviewLoadProfile("relaxed");
        settings.setDuplicateSideAEnabled(false);
        settings.setDuplicateSideBEnabled(true);
        return settings;
    }
}
