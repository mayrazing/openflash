package openflash_plugin.tts.controller;

import openflash_plugin.tts.service.TtsService;
import openflash_plugin.tts.service.impl.TtsFeatureGuard;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import openflash_core.entity.User;
import openflash_core.service.CurrentUserService;

class TtsControllerTest {

    @Test
    void postAudioUsesDeckDefaultModel() throws Exception {
        TtsService ttsService = mock(TtsService.class);
        CurrentUserService currentUserService = currentUserService(7L);
        when(ttsService.getAudioBytes(7L, 11L, "hello")).thenReturn(new byte[] { 1, 2, 3 });
        MockMvc mvc = mvc(ttsService, currentUserService, mock(TtsFeatureGuard.class));

        mvc.perform(post("/api/tts")
                .contentType("application/json")
                .content("{\"deckId\":11,\"text\":\"hello\"}"))
            .andExpect(status().isOk());

        verify(ttsService).getAudioBytes(7L, 11L, "hello");
    }

    @Test
    void postModelPreviewUsesTheExplicitCandidateModel() throws Exception {
        TtsService ttsService = mock(TtsService.class);
        CurrentUserService currentUserService = currentUserService(7L);
        when(ttsService.getAudioBytes(7L, "hello", "piper")).thenReturn(new byte[] { 1, 2, 3 });
        MockMvc mvc = mvc(ttsService, currentUserService, mock(TtsFeatureGuard.class));

        mvc.perform(post("/api/tts/piper")
                .contentType("application/json")
                .content("{\"text\":\"hello\"}"))
            .andExpect(status().isOk());

        verify(ttsService).getAudioBytes(7L, "hello", "piper");
    }

    @Test
    void postAudioReturnsFeatureDisabledBeforeReadingCurrentUser() throws Exception {
        TtsService ttsService = mock(TtsService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        TtsFeatureGuard guard = mock(TtsFeatureGuard.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED)).when(guard).ensureTtsEnabled();
        MockMvc mvc = mvc(ttsService, currentUserService, guard);

        mvc.perform(post("/api/tts")
                .contentType("application/json")
                .content("{\"text\":\"hello\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));

        verify(currentUserService, never()).getCurrentUser();
        verify(ttsService, never()).getAudioBytes(7L, (Long) null, "hello");
    }

    @Test
    void getAudioEndpointIsNotAvailable() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TtsController(
                mock(TtsService.class), mock(CurrentUserService.class), mock(TtsFeatureGuard.class)))
            .build();

        mvc.perform(get("/api/tts").param("text", "private card text"))
            .andExpect(status().isMethodNotAllowed());
    }

    private static CurrentUserService currentUserService(Long userId) {
        CurrentUserService service = mock(CurrentUserService.class);
        User user = new User();
        user.setId(userId);
        when(service.getCurrentUser()).thenReturn(user);
        return service;
    }

    private static MockMvc mvc(
            TtsService ttsService,
            CurrentUserService currentUserService,
            TtsFeatureGuard guard) {
        return MockMvcBuilders
            .standaloneSetup(new TtsController(ttsService, currentUserService, guard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
