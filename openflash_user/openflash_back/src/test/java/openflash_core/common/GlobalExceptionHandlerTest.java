package openflash_core.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class GlobalExceptionHandlerTest {

    /** 合成上游错误码，仅用于验证 502 映射逻辑，不依赖任何插件错误码（boundary 禁止 core 测试引用插件包）。 */
    private static AppErrorCode synthetic(int value, String name) {
        return new AppErrorCode() {
            @Override
            public int value() {
                return value;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @RestController
    static class FakeController {
        @GetMapping("/test/app-exception")
        String appException() {
            throw new AppException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        @GetMapping("/test/unauthorized")
        String unauthorized() {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        @GetMapping("/test/feature-disabled")
        String featureDisabled() {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }

        @GetMapping("/test/deck-not-found")
        String deckNotFound() {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }

        @GetMapping("/test/ai-upstream")
        String aiUpstream() {
            throw new AppException(synthetic(50201, "SYNTHETIC_UPSTREAM_502_A"));
        }

        @GetMapping("/test/tts-upstream")
        String ttsUpstream() {
            throw new AppException(synthetic(50207, "SYNTHETIC_UPSTREAM_502_B"));
        }

        @GetMapping("/test/generic")
        String generic() {
            throw new RuntimeException("boom");
        }

        @PostMapping("/test/json")
        String json(@RequestBody JsonNode body) {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new FakeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void appException400ReturnsCodeAndNoMessage() throws Exception {
        mvc.perform(get("/test/app-exception"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40010))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void appException401ReturnsUnauthorized() throws Exception {
        mvc.perform(get("/test/unauthorized"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void appException503ReturnsServiceUnavailable() throws Exception {
        mvc.perform(get("/test/feature-disabled"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    void deckNotFoundReturnsNotFoundWithExistingErrorCode() throws Exception {
        mvc.perform(get("/test/deck-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40020));
    }

    @Test
    void appException502ReturnsBadGateway() throws Exception {
        mvc.perform(get("/test/ai-upstream"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value(50201));
    }

    @Test
    void ttsUpstreamExceptionReturnsBadGatewayWithCode() throws Exception {
        mvc.perform(get("/test/tts-upstream"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value(50207));
    }

    @Test
    void unhandledExceptionReturnsGenericCode() throws Exception {
        mvc.perform(get("/test/generic"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void malformedJsonOutsideInternalAdminKeepsGeneric500Contract() throws Exception {
        mvc.perform(post("/test/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(50000));
    }
}
