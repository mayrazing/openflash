package openflash_core.service.impl;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.AnthropicClientAsyncImpl;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.core.LogLevel;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import openflash_core.security.OutboundUrlValidator.ResolvedTarget;

/** 为生成请求创建只连接到已校验地址的 Anthropic SDK 客户端. */
final class PinnedAnthropicClients {

    private PinnedAnthropicClients() {}

    static ClientPair build(
            ResolvedTarget target,
            String apiKey,
            Duration timeout,
            LogLevel logLevel) {
        return new ClientPair(
                new AnthropicClientImpl(buildOptions(target, apiKey, timeout, logLevel)),
                new AnthropicClientAsyncImpl(buildOptions(target, apiKey, timeout, logLevel)));
    }

    private static ClientOptions buildOptions(
            ResolvedTarget target,
            String apiKey,
            Duration timeout,
            LogLevel logLevel) {
        AnthropicBackend backend = AnthropicBackend.builder()
                .baseUrl(target.uri().toString())
                .apiKey(apiKey)
                .build();
        com.anthropic.client.okhttp.OkHttpClient transport =
                new com.anthropic.client.okhttp.OkHttpClient(
                        buildHttpClient(target, timeout), backend);
        ClientOptions.Builder options = ClientOptions.builder()
                .httpClient(transport)
                .timeout(timeout)
                .logLevel(logLevel);
        backend.applyCredentials(transport, options);
        return options.build();
    }

    /** 固定 DNS 到校验时解析的地址, 保留原 host 做 TLS/SNI 校验. */
    static OkHttpClient buildHttpClient(ResolvedTarget target, Duration timeout) {
        String approvedHost = target.uri().getHost();
        Dns pinnedDns = host -> host.equalsIgnoreCase(approvedHost)
                ? target.addresses()
                : List.of();
        return new OkHttpClient.Builder()
                .dns(pinnedDns)
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
    }

    record ClientPair(AnthropicClient sync, AnthropicClientAsync async) {}
}
