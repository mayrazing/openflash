package openflash_ai_runtime;

import openflash_ai_runtime.security.InternalTokenGuard;
import openflash_ai_runtime.security.InternalAccessFilter;
import openflash_ai_runtime.security.GenerationRequestSizeFilter;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.test.json.JsonCompareMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = AiRuntimeApplication.class,
    properties = {
        "app.codex.enabled=false",
        "app.internal.admin-token=boot-admin-token",
        "app.internal.core-token=boot-core-token",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
    })
@AutoConfigureMockMvc
@Import(AiRuntimeApplicationTest.TestEndpoints.class)
class AiRuntimeApplicationTest {

    private static final String ADMIN_TOKEN = "boot-admin-token";
    private static final String CORE_TOKEN = "boot-core-token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InternalAccessFilter internalAccessFilter;

    @Autowired
    private GenerationRequestSizeFilter generationRequestSizeFilter;

    @Test
    void loadsProductionContextAndSerializesTheExactPublicHealthResponse() throws Exception {
        assertThat(objectMapper.getClass().getName()).startsWith("tools.jackson.");
        assertThat(internalAccessFilter).isNotNull();
        assertThat(internalAccessFilter.getClass().getAnnotation(Order.class).value())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(generationRequestSizeFilter.getClass().getAnnotation(Order.class).value())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);

        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void rootShowsThePublicStartedPage() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("OpenFlash AI Runtime")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Status: <strong>UP</strong>")));
    }

    @Test
    void productionFilterSerializesForbiddenResponsesWithBootToolsJackson() throws Exception {
        String body = mvc.perform(get("/external-test"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(objectMapper.readTree(body).get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void enforcesDistinctConfiguredTokensForBothInternalScopes() throws Exception {
        mvc.perform(get("/api/internal/admin/test/ping")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().string("admin"));
        mvc.perform(get("/api/internal/core/test/ping")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().string("core"));

        assertForbidden(get("/api/internal/admin/test/ping"));
        assertForbidden(get("/api/internal/admin/test/ping")
            .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ""));
        assertForbidden(get("/api/internal/admin/test/ping")
            .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, "wrong"));
        assertForbidden(get("/api/internal/admin/test/ping")
            .header(InternalTokenGuard.CORE_TOKEN_HEADER, ADMIN_TOKEN));
        assertForbidden(get("/api/internal/core/test/ping"));
        assertForbidden(get("/api/internal/core/test/ping")
            .header(InternalTokenGuard.CORE_TOKEN_HEADER, ""));
        assertForbidden(get("/api/internal/core/test/ping")
            .header(InternalTokenGuard.CORE_TOKEN_HEADER, "wrong"));
        assertForbidden(get("/api/internal/core/test/ping")
            .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, CORE_TOKEN));
    }

    @Test
    void rejectsEveryPathOutsideTheTwoScopesAndExactPublicPaths() throws Exception {
        assertForbidden(get("/external-test"));
        assertForbidden(get("/index.html"));
        assertForbidden(get("/health/"));
        assertForbidden(get("/health;x=1"));
        assertForbidden(get("/health%3Bx=1"));
        assertForbidden(get("/health-check"));
    }

    @Test
    void healthQueryStringDoesNotChangeTheExactPublicPath() throws Exception {
        mvc.perform(get("/health").queryParam("probe", "ready"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void unauthenticatedInternalRoutingMismatchesAreForbiddenBeforeMvc() throws Exception {
        assertForbidden(post("/api/internal/admin/test/ping"));
        assertForbidden(post("/api/internal/core/test/body")
            .contentType(MediaType.TEXT_PLAIN)
            .content("not-json"));
        assertForbidden(get("/api/internal/core/test/json")
            .accept(MediaType.APPLICATION_XML));
    }

    @Test
    void authenticationRunsBeforeGenerationBodyLimitAndAuthenticatedOversizeIsSafeBadRequest()
            throws Exception {
        String oversized = "x".repeat(GenerationRequestValidator.MAX_JSON_BODY_BYTES + 1);

        mvc.perform(post("/api/internal/core/platform-ai/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversized))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(post("/api/internal/core/platform-ai/generations")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversized))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authenticatedInternalRoutingMismatchesGetSafeMvcErrors() throws Exception {
        mvc.perform(post("/api/internal/admin/test/ping")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/internal/core/test/body")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                .contentType(MediaType.TEXT_PLAIN)
                .content("not-json"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/internal/core/test/json")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void validInternalTokenGetsSafeNotFoundForAnUnknownInternalPath() throws Exception {
        mvc.perform(get("/api/internal/admin/does-not-exist")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40401))
            .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/internal/core/does-not-exist")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40401))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unsupportedHealthMethodIsNotPartOfThePublicSurface() throws Exception {
        mvc.perform(post("/"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301))
            .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/health"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unreadableInternalBodyGetsSafeBadRequest() throws Exception {
        mvc.perform(post("/api/internal/core/test/body")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void productionJsonMapperRejectsUnknownPlatformAdminFieldsBeforeService() throws Exception {
        String body = mvc.perform(post("/api/internal/admin/platform-ai/connections")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"API","protocol":"ANTHROPIC","cliKey":null,
                     "baseUrl":"https://api.example.test","sortOrder":0,
                     "apiKey":"must-not-reach-service"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("must-not-reach-service");
    }

    @Test
    void invalidInternalArgumentBindingGetsSafeBadRequest() throws Exception {
        mvc.perform(get("/api/internal/core/test/binding")
                .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                .queryParam("count", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownInternalExceptionGetsOnlySafeGenericResponse() throws Exception {
        String body = mvc.perform(get("/api/internal/admin/test/explode")
                .header(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(body).doesNotContain(
            ADMIN_TOKEN, CORE_TOKEN, "CODEX_HOME", "/private/runtime/path", "stderr");
    }

    private void assertForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @TestConfiguration
    static class TestEndpoints {

        @Bean
        InternalTestController internalTestController() {
            return new InternalTestController();
        }
    }

    @RestController
    static class InternalTestController {

        @GetMapping("/api/internal/admin/test/ping")
        String adminPing() {
            return "admin";
        }

        @GetMapping("/api/internal/core/test/ping")
        String corePing() {
            return "core";
        }

        @PostMapping(
            path = "/api/internal/core/test/body",
            consumes = MediaType.APPLICATION_JSON_VALUE)
        void body(@RequestBody Input input) {
        }

        @GetMapping(
            path = "/api/internal/core/test/json",
            produces = MediaType.APPLICATION_JSON_VALUE)
        Input json() {
            return new Input("safe");
        }

        @GetMapping("/api/internal/core/test/binding")
        void binding(@RequestParam int count) {
        }

        @GetMapping("/api/internal/admin/test/explode")
        void explode() {
            throw new IllegalStateException(
                "stderr /private/runtime/path CODEX_HOME " + ADMIN_TOKEN + " " + CORE_TOKEN);
        }

        @GetMapping("/external-test")
        String external() {
            return "must not be public";
        }
    }

    private record Input(String value) {
    }
}
