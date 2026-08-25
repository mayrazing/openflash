package openflash_core.service.impl;

import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import openflash_core.security.OutboundUrlValidator.ResolvedTarget;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.AuthStyle;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.ModelListTransport;
import openflash_core.service.impl.AiModelDiscoveryServiceImpl.UpstreamAttemptFailed;
import org.springframework.stereotype.Component;

/**
 * 安全模型列表 HTTP 客户端：仅按 ResolvedTarget.addresses 解析 DNS（防 SSRF），
 * 拒绝重定向，限制响应体大小，覆盖两种鉴权头形态。
 * <p>不做候选/解析逻辑（由 {@link AiModelDiscoveryServiceImpl} 负责），只承担一次网络请求。
 */
@Component
class SafeModelListHttpClient implements ModelListTransport {

    /** 模型列表响应一般 < 几十 KiB；256 KiB 上限挡住异常巨响应消耗内存。 */
    private static final long MAX_BODY_BYTES = 256L * 1024L;

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @Override
    public String get(ResolvedTarget target, String apiKey, AuthStyle authStyle) {
        OkHttpClient client = buildClient(target);
        Request.Builder builder =
                new Request.Builder().url(target.uri().toString()).get();
        if (authStyle == AuthStyle.ANTHROPIC) {
            builder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new UpstreamAttemptFailed();
            ResponseBody body = response.body();
            if (body == null) throw new UpstreamAttemptFailed();
            // 体积保护：先 request(MAX+1) 探测是否超上限（true 表示已缓冲到这么多字节，即已超限），
            // 不超限再 readByteArray() 单次拿全量。两步均作用于同一 BufferedSource，无重复读取。
            BufferedSource source = body.source();
            if (source.request(MAX_BODY_BYTES + 1)) throw new UpstreamAttemptFailed();
            return new String(source.readByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UpstreamAttemptFailed(ex);
        }
    }

    /** 构造请求级 OkHttpClient：DNS 锁定到 ResolvedTarget，强制直连，不跟随重定向。包私有便于测试断言。 */
    static OkHttpClient buildClient(ResolvedTarget target) {
        // 用 validator 已解析过的 IP 列表锁定 DNS：保证 SSRF 校验后到 connect 之间不被 DNS 重绑定。
        // 仍以 target.uri().getHost() 发起请求，保留 hostname 用于 TLS/SNI 校验。
        Dns pinnedDns = host -> host.equalsIgnoreCase(target.uri().getHost())
                ? target.addresses()
                : List.of();
        // 每个请求新建 OkHttpClient：Dns 是 client 级配置，按 ResolvedTarget 锁定 IP；
        // 复用单例需在请求级动态切换 Dns，徒增复杂度。这里依赖 OkHttp 自身连接池/线程池的 idle 回收。
        return new OkHttpClient.Builder()
                .dns(pinnedDns)
                .proxy(Proxy.NO_PROXY) // 强制直连：避免出站代理介入后绕过 DNS pinning，让 SSRF 防护失效
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(TIMEOUT)
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .writeTimeout(TIMEOUT)
                .build();
    }
}
