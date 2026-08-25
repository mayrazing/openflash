package openflash_ai_runtime.security;

import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAccessFilterTest {

    private static final String ADMIN_TOKEN = "admin-test-token";
    private static final String CORE_TOKEN = "core-test-token";

    @Test
    void rejectsScopedRequestsInsideTheFilterWhenServerTokensAreEmpty() throws Exception {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        InternalAccessFilter filter = filter(properties);
        MockHttpServletRequest request = request("GET", "/api/internal/admin/test");
        request.addHeader(InternalTokenGuard.ADMIN_TOKEN_HEADER, ADMIN_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":40301")
            .doesNotContain(ADMIN_TOKEN, CORE_TOKEN);
    }

    @Test
    void usesRawUriAfterRemovingOnlyTheContextPath() throws Exception {
        InternalAccessFilter filter = configuredFilter();

        assertChainResult(filter, "GET", "/runtime/health", "/runtime", null, true);
        assertChainResult(filter, "GET", "/runtime/health;x=1", "/runtime", null, false);
        assertChainResult(filter, "GET", "/runtime/health%3Bx=1", "/runtime", null, false);
        assertChainResult(filter, "GET", "/runtime/health/", "/runtime", null, false);
        assertChainResult(filter, "POST", "/runtime/health", "/runtime", null, false);
    }

    @Test
    void acceptsOnlyExactScopedBasesAndSlashSubpaths() throws Exception {
        InternalAccessFilter filter = configuredFilter();

        assertChainResult(filter, "GET", "/api/internal/admin", "", ADMIN_TOKEN, true);
        assertChainResult(filter, "GET", "/api/internal/admin/test", "", ADMIN_TOKEN, true);
        assertChainResult(filter, "GET", "/api/internal/adminish", "", ADMIN_TOKEN, false);
        assertChainResult(filter, "GET", "/api/internal/admin;x=1", "", ADMIN_TOKEN, false);
        assertChainResult(filter, "GET", "/api/internal/core", "", CORE_TOKEN, true);
        assertChainResult(filter, "GET", "/api/internal/core/test", "", CORE_TOKEN, true);
        assertChainResult(filter, "GET", "/api/internal/core%2Ftest", "", CORE_TOKEN, false);
    }

    private void assertChainResult(
            InternalAccessFilter filter,
            String method,
            String requestUri,
            String contextPath,
            String token,
            boolean expectedChainCall) throws Exception {
        MockHttpServletRequest request = request(method, requestUri);
        request.setContextPath(contextPath);
        if (token != null) {
            String header = requestUri.contains("/admin")
                ? InternalTokenGuard.ADMIN_TOKEN_HEADER
                : InternalTokenGuard.CORE_TOKEN_HEADER;
            request.addHeader(header, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            chainCalled.set(true));

        assertThat(chainCalled.get()).isEqualTo(expectedChainCall);
        assertThat(response.getStatus()).isEqualTo(expectedChainCall ? 200 : 403);
    }

    private MockHttpServletRequest request(String method, String requestUri) {
        return new MockHttpServletRequest(method, requestUri);
    }

    private InternalAccessFilter configuredFilter() {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        properties.getInternal().setAdminToken(ADMIN_TOKEN);
        properties.getInternal().setCoreToken(CORE_TOKEN);
        return filter(properties);
    }

    private InternalAccessFilter filter(AiRuntimeProperties properties) {
        return new InternalAccessFilter(
            new InternalTokenGuard(properties),
            new SafeErrorResponseWriter(new ObjectMapper()));
    }
}
