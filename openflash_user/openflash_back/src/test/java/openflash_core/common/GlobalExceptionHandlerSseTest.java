package openflash_core.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import openflash_core.controller.SseController;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserSseRegistry;

class GlobalExceptionHandlerSseTest {

    @Test
    void sseUnauthorizedRequestReturnsUnauthorizedWithoutJsonBody() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserId()).thenThrow(new AppException(ErrorCode.UNAUTHORIZED));
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SseController(new UserSseRegistry(), currentUserService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        ResultActions result = assertDoesNotThrow(() ->
            mvc.perform(get("/api/sse/notifications").accept(MediaType.TEXT_EVENT_STREAM))
        );
        result.andExpect(status().isUnauthorized())
            .andExpect(content().string(""));
    }

    @Test
    void sseUnexpectedExceptionReturnsServerErrorWithoutJsonBody() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new BrokenSseController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        ResultActions result = assertDoesNotThrow(() ->
            mvc.perform(get("/api/sse/broken").accept(MediaType.TEXT_EVENT_STREAM))
        );
        result.andExpect(status().isInternalServerError())
            .andExpect(content().string(""));
    }

    @Test
    void sseTimeoutReturnsServiceUnavailableWithoutJsonBody() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TimeoutSseController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        ResultActions result = assertDoesNotThrow(() ->
            mvc.perform(get("/api/sse/timeout").accept(MediaType.TEXT_EVENT_STREAM))
        );
        result.andExpect(status().isServiceUnavailable())
            .andExpect(content().string(""));
    }

    @Test
    void disconnectedSseClientDoesNotProduceAnotherResponse() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DisconnectedSseController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/api/sse/disconnected").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test
    void normalTimeoutReturnsServiceUnavailableWithJsonBody() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TimeoutNormalController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/api/cards/timeout").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void normalWildcardAcceptRequestReturnsJsonErrorBody() throws Exception {
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new BrokenNormalController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/api/cards/broken").accept(MediaType.ALL))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40010))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @RestController
    private static class BrokenSseController {

        @GetMapping(value = "/api/sse/broken", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter broken() {
            throw new IllegalStateException("boom");
        }
    }

    @RestController
    private static class TimeoutSseController {

        @GetMapping(value = "/api/sse/timeout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter timeout() {
            throw new AsyncRequestTimeoutException();
        }
    }

    @RestController
    private static class DisconnectedSseController {

        @GetMapping(value = "/api/sse/disconnected", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter disconnected() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Client disconnected");
        }
    }

    @RestController
    private static class BrokenNormalController {

        @GetMapping("/api/cards/broken")
        String broken() {
            throw new AppException(ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    @RestController
    private static class TimeoutNormalController {

        @GetMapping("/api/cards/timeout")
        String timeout() {
            throw new AsyncRequestTimeoutException();
        }
    }
}
