package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import openflash_core.security.OutboundUrlValidator;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.AuthStyle;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.UpstreamAttemptFailed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 安全 transport 网络测试：DNS 锁定、不跟随 301、超大响应失败、认证头形态。 */
class SafeModelListHttpClientTest {

    private MockWebServer server;
    private SafeModelListHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        client = new SafeModelListHttpClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void anthropicAuthSendsApiKeyAndVersionHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));
        OutboundUrlValidator.ResolvedTarget target = targetFor("/v1/models");
        client.get(target, "secret-key", AuthStyle.ANTHROPIC);
        RecordedRequest req = server.takeRequest();
        assertEquals("secret-key", req.getHeader("x-api-key"));
        assertEquals("2023-06-01", req.getHeader("anthropic-version"));
        assertEquals(null, req.getHeader("Authorization"));
    }

    @Test
    void bearerAuthSendsAuthorizationHeader() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));
        OutboundUrlValidator.ResolvedTarget target = targetFor("/models");
        client.get(target, "k1", AuthStyle.BEARER);
        RecordedRequest req = server.takeRequest();
        assertEquals("Bearer k1", req.getHeader("Authorization"));
        assertEquals(null, req.getHeader("x-api-key"));
    }

    @Test
    void rejectsRedirect() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(301)
                        .addHeader("Location", "https://evil.example/"));
        OutboundUrlValidator.ResolvedTarget target = targetFor("/v1/models");
        assertThrows(
                UpstreamAttemptFailed.class,
                () -> client.get(target, "k", AuthStyle.ANTHROPIC));
    }

    @Test
    void rejectsOversizedBody() {
        // MAX_BODY_BYTES = 256 KiB；写 257 KiB 触发上限。
        Buffer buf = new Buffer();
        byte[] chunk = new byte[1024];
        for (int i = 0; i < 257; i++) buf.write(chunk);
        server.enqueue(new MockResponse().setResponseCode(200).setBody(buf));
        OutboundUrlValidator.ResolvedTarget target = targetFor("/v1/models");
        assertThrows(
                UpstreamAttemptFailed.class,
                () -> client.get(target, "k", AuthStyle.BEARER));
    }

    @Test
    void rejectsNon2xx() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        OutboundUrlValidator.ResolvedTarget target = targetFor("/v1/models");
        assertThrows(
                UpstreamAttemptFailed.class,
                () -> client.get(target, "k", AuthStyle.BEARER));
    }

    @Test
    void dnsLookupRestrictedToTargetAddresses() throws Exception {
        // 用一个不解析的 host name，但传入回环地址。OkHttp 自定义 dns 命中即可连通。
        URI uri = URI.create(
                "http://nonexistent.invalid:" + server.getPort() + "/v1/models");
        OutboundUrlValidator.ResolvedTarget target =
                new OutboundUrlValidator.ResolvedTarget(
                        uri, List.of(InetAddress.getByName("127.0.0.1")));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"data\":[]}"));
        String body = client.get(target, "k", AuthStyle.BEARER);
        assertNotNull(body);
        assertEquals("nonexistent.invalid", server.takeRequest().getHeader("Host").split(":")[0]);
    }

    @Test
    void usesNoProxyEvenWhenSystemPropsSet() {
        // 即使 JVM 全局代理属性被设置，buildClient 仍应强制 Proxy.NO_PROXY，
        // 否则 OkHttp 会让代理自己解析 hostname，绕过 ResolvedTarget 的 DNS pinning（SSRF 防护失效）。
        String prevHttpsHost = System.getProperty("https.proxyHost");
        String prevHttpsPort = System.getProperty("https.proxyPort");
        String prevHttpHost = System.getProperty("http.proxyHost");
        String prevHttpPort = System.getProperty("http.proxyPort");
        try {
            System.setProperty("https.proxyHost", "127.0.0.1");
            System.setProperty("https.proxyPort", "9");
            System.setProperty("http.proxyHost", "127.0.0.1");
            System.setProperty("http.proxyPort", "9");
            OutboundUrlValidator.ResolvedTarget target = targetFor("/v1/models");
            OkHttpClient c = SafeModelListHttpClient.buildClient(target);
            assertEquals(Proxy.NO_PROXY, c.proxy());
        } finally {
            restoreProp("https.proxyHost", prevHttpsHost);
            restoreProp("https.proxyPort", prevHttpsPort);
            restoreProp("http.proxyHost", prevHttpHost);
            restoreProp("http.proxyPort", prevHttpPort);
        }
    }

    private static void restoreProp(String key, String prev) {
        if (prev != null) System.setProperty(key, prev);
        else System.clearProperty(key);
    }

    private OutboundUrlValidator.ResolvedTarget targetFor(String path) {
        URI uri = server.url(path).uri();
        try {
            return new OutboundUrlValidator.ResolvedTarget(
                    uri, List.of(InetAddress.getByName(uri.getHost())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
