package openflash_ai_runtime.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import openflash_ai_runtime.AiRuntimeApplication;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = AiRuntimeApplication.class,
        properties = {
            "app.codex.enabled=true",
            "app.internal.admin-token=boot-admin-token",
            "app.internal.core-token=boot-core-token",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
        })
@AutoConfigureMockMvc
class GenerationRequestSizeFilterBootTest {

    private static final String CORE_TOKEN = "boot-core-token";
    private static final String PLATFORM_PATH =
            "/api/internal/core/platform-ai/generations";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlatformAiCatalogService platformService;

    @MockitoBean
    private RuntimeSystemConfigService runtimeSystemConfigService;

    @BeforeEach
    void allowControllerCallsToExposeAnyFilterBypass() {
        when(platformService.generate(any())).thenReturn("platform-answer");
    }

    @Test
    void exactSmallGenerationBodyStillReachesController()
            throws Exception {
        mvc.perform(post(PLATFORM_PATH)
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());

        verify(platformService).generate(any());
    }

    @Test
    void exactOversizeGenerationBodiesKeepThe512KibLimit()
            throws Exception {
        mvc.perform(post(PLATFORM_PATH)
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizeValidBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        verifyNoInteractions(platformService);
    }

    @Test
    void authenticatedSmallMatrixBodiesInSecurityScopeSegmentsStayForbidden()
            throws Exception {
        for (String matrixPath : scopeMatrixVariants(PLATFORM_PATH)) {
            assertAuthenticatedScopeMatrixForbidden(matrixPath, validBody());
        }

        verifyNoInteractions(platformService);
    }

    @Test
    void authenticatedOversizeMatrixBodiesInSecurityScopeSegmentsStayForbidden()
            throws Exception {
        for (String matrixPath : scopeMatrixVariants(PLATFORM_PATH)) {
            assertAuthenticatedScopeMatrixForbidden(matrixPath, oversizeValidBody());
        }

        verifyNoInteractions(platformService);
    }

    @Test
    void authenticatedSmallMatrixBodiesInBusinessSegmentsAreRejectedBeforeControllers()
            throws Exception {
        for (String matrixPath : businessMatrixVariants(PLATFORM_PATH)) {
            assertMatrixRejected(matrixPath, validBody());
        }

        verifyNoInteractions(platformService);
    }

    @Test
    void authenticatedOversizeMatrixBodiesInBusinessSegmentsAreRejectedBeforeControllers()
            throws Exception {
        for (String matrixPath : businessMatrixVariants(PLATFORM_PATH)) {
            assertMatrixRejected(matrixPath, oversizeValidBody());
        }

        verifyNoInteractions(platformService);
    }

    @Test
    void encodedMatrixVariantsAreRejectedWithoutBroadeningToNeighborPaths()
            throws Exception {
        for (String semicolon : new String[] {"%3B", "%3b"}) {
            mvc.perform(post(URI.create(PLATFORM_PATH + semicolon + "session=encoded"))
                            .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(40401));
        }

        mvc.perform(post(PLATFORM_PATH + "-extra")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizeValidBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mvc.perform(post(PLATFORM_PATH + ";matrix=value/extra")
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40001));

        String middleMatrixWithExtra = PLATFORM_PATH.replace(
                "/platform-ai/", "/platform-ai;matrix=value/") + "/extra";
        mvc.perform(post(URI.create(middleMatrixWithExtra))
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40001));

        verifyNoInteractions(platformService);
    }

    @Test
    void authenticationStillRunsBeforeMatrixRequestRejection() throws Exception {
        for (String matrixPath : matrixVariants(PLATFORM_PATH)) {
            mvc.perform(post(URI.create(matrixPath))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(oversizeValidBody()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
        }

        verifyNoInteractions(platformService);
    }

    private static String oversizeValidBody() {
        return validBody()
                + " ".repeat(GenerationRequestValidator.MAX_JSON_BODY_BYTES + 1);
    }

    private void assertMatrixRejected(String matrixPath, String body) throws Exception {
        mvc.perform(post(URI.create(matrixPath))
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private void assertAuthenticatedScopeMatrixForbidden(String matrixPath, String body)
            throws Exception {
        mvc.perform(post(URI.create(matrixPath))
                        .header(InternalTokenGuard.CORE_TOKEN_HEADER, CORE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    private static List<String> matrixVariants(String exactPath) {
        return matrixVariants(exactPath, 0, exactPath.substring(1).split("/").length);
    }

    private static List<String> scopeMatrixVariants(String exactPath) {
        return matrixVariants(exactPath, 0, 3);
    }

    private static List<String> businessMatrixVariants(String exactPath) {
        return matrixVariants(exactPath, 3, exactPath.substring(1).split("/").length);
    }

    private static List<String> matrixVariants(
            String exactPath, int firstSegment, int endSegment) {
        String[] segments = exactPath.substring(1).split("/");
        List<String> variants = new ArrayList<>();
        for (int matrixIndex = firstSegment; matrixIndex < endSegment; matrixIndex++) {
            variants.add(withMatrix(segments, matrixIndex));
        }
        String[] multiple = segments.clone();
        for (int matrixIndex = firstSegment; matrixIndex < endSegment; matrixIndex++) {
            multiple[matrixIndex] += ";multiple=" + matrixIndex;
        }
        variants.add("/" + String.join("/", multiple));
        return variants;
    }

    private static String withMatrix(String[] exactSegments, int matrixIndex) {
        String[] copy = exactSegments.clone();
        copy[matrixIndex] += ";segment=" + matrixIndex;
        return "/" + String.join("/", copy);
    }

    private static String validBody() {
        return """
                {"requestId":"12345678-1234-4234-9234-123456789abc",
                 "userId":7,"offeringKey":"platform-api-model",
                 "model":"claude","prompt":"safe"}
                """;
    }
}
