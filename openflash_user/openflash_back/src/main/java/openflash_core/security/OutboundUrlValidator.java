package openflash_core.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 出站 URL 校验器：仅放行 https 公网地址，阻断回环/私网/链路本地/组播/IPv6 ULA。
 * 用于防止用户配置的上游接口指向内网造成 SSRF。
 */
@Component
public class OutboundUrlValidator {

    private final AddressResolver resolver;

    /** 默认实现走 JDK 的 DNS 解析。 */
    public OutboundUrlValidator() {
        this(host -> List.of(InetAddress.getAllByName(host)));
    }

    /** 测试注入用：包私有，便于替换解析器避免触发真实 DNS。 */
    OutboundUrlValidator(AddressResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 测试专用工厂：返回永远放行的校验器（解析为固定公网地址 8.8.8.8），
     * 仅供测试代码替代真实 DNS，禁止在生产代码中调用。
     */
    public static OutboundUrlValidator permissiveForTesting() {
        return new OutboundUrlValidator(host -> {
            try {
                return List.of(InetAddress.getByName("8.8.8.8"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** 解析并校验 HTTPS 公网地址；任一 DNS 结果不可用时整体拒绝。 */
    public ResolvedTarget resolve(String rawUrl) {
        try {
            URI uri = new URI(rawUrl == null ? "" : rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid outbound URL");
            }
            List<InetAddress> addresses = List.copyOf(resolver.resolve(uri.getHost()));
            if (addresses.isEmpty() || addresses.stream().anyMatch(this::isBlocked)) {
                throw new IllegalArgumentException("blocked outbound address");
            }
            return new ResolvedTarget(uri, addresses);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("cannot resolve outbound URL", ex);
        }
    }

    /** 判断本机、私网、链路本地、组播、未指定及 IPv6 ULA 地址。 */
    private boolean isBlocked(InetAddress address) {
        byte[] b = address.getAddress();
        // 先用 JDK 内置语义覆盖通用私网/链路本地/回环/组播。
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return true;
        // 显式重列 IPv4 私网与 169.254/127/10/0 段，防止 JDK 内置语义在不同发行版差异时漏挡（防御性冗余，刻意保留）。
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255;
            return a == 0
                    || a == 10
                    || a == 127
                    || (a == 169 && c == 254)
                    || (a == 172 && c >= 16 && c <= 31)
                    || (a == 192 && c == 168);
        }
        // IPv6：仅过滤 ULA fc00::/7；非 16 字节地址族未知 → 保守拒绝。
        return b.length != 16 || ((b[0] & 0xfe) == 0xfc);
    }

    /** 解析器抽象，便于测试注入。 */
    @FunctionalInterface
    interface AddressResolver {
        List<InetAddress> resolve(String host) throws Exception;
    }

    /** 校验通过的目标：原始 URI 加全部解析地址，供调用方按需使用。 */
    public record ResolvedTarget(URI uri, List<InetAddress> addresses) {}
}
