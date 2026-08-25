package openflash_ai_runtime.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new RuntimeExceptionHandler(
                new SafeErrorResponseWriter(new ObjectMapper())))
            .build();
    }

    @Test
    void mapsKnownRuntimeErrorsToTheirExactSafeCodes() throws Exception {
        mockMvc.perform(get("/known/invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/known/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(get("/known/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/known/unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void safeErrorsIgnoreAnUnacceptableResponseType() throws Exception {
        mockMvc.perform(get("/known/forbidden").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(40301))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void mapsUnreadableRequestsToInvalidInternalRequest() throws Exception {
        mockMvc.perform(post("/unreadable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void unknownExceptionsReturnOnlyTheGenericSafeResponse() throws Exception {
        String body = mockMvc.perform(get("/unknown"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("admin-secret", "core-secret", "OPENFLASH_AI_RUNTIME_ADMIN_TOKEN",
                "CODEX_HOME", "/private/runtime/path", "stderr", "upstream exploded");
    }

    @RestController
    private static class FailingController {

        @GetMapping("/known/invalid")
        void invalid() {
            throw new RuntimeException(RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        }

        @GetMapping("/known/forbidden")
        void forbidden() {
            throw new RuntimeException(RuntimeErrorCode.FORBIDDEN);
        }

        @GetMapping("/known/not-found")
        void notFound() {
            throw new RuntimeException(RuntimeErrorCode.NOT_FOUND);
        }

        @GetMapping("/known/unavailable")
        void unavailable() {
            throw new RuntimeException(RuntimeErrorCode.UNAVAILABLE);
        }

        @PostMapping("/unreadable")
        void unreadable(@RequestBody Input input) {
        }

        @GetMapping("/unknown")
        void unknown() {
            throw new IllegalStateException(
                "upstream exploded stderr /private/runtime/path CODEX_HOME admin-secret core-secret");
        }

        private record Input(String value) {
        }
    }
}
