package openflash_core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.util.List;
import openflash_core.security.OutboundUrlValidator.AddressResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OutboundUrlValidator 契约测试：
 * 仅放行 https 公网；拒绝 user-info、fragment、非 https；
 * 拒绝任一 DNS 结果落在回环、私网、链路本地、组播、未指定、IPv6 ULA。
 */
class OutboundUrlValidatorTest {

    /** 用固定解析器构造 validator，避免触发真实 DNS。 */
    private static OutboundUrlValidator validator(AddressResolver resolver) {
        return new OutboundUrlValidator(resolver);
    }

    /** 直接由字面量构造 InetAddress，不走 DNS。 */
    private static InetAddress address(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void acceptsPublicHttps() {
        var validator = validator(host -> List.of(address("8.8.8.8")));
        var target = validator.resolve("https://api.example.com/anthropic");
        assertEquals("api.example.com", target.uri().getHost());
        assertEquals(List.of(address("8.8.8.8")), target.addresses());
    }

    @Test
    void acceptsPublicIpv6() {
        var validator = validator(host -> List.of(address("2001:db8::1")));
        var target = validator.resolve("https://api.example.com");
        assertEquals("api.example.com", target.uri().getHost());
    }

    @Test
    void trimsRawUrl() {
        var validator = validator(host -> List.of(address("8.8.8.8")));
        var target = validator.resolve("  https://api.example.com  ");
        assertEquals("api.example.com", target.uri().getHost());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.example.com", "https://u:p@api.example.com", "not-a-url"})
    void rejectsInvalidUrl(String url) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator(host -> List.of(address("8.8.8.8"))).resolve(url));
    }

    @Test
    void rejectsNullAndBlank() {
        var validator = validator(host -> List.of(address("8.8.8.8")));
        assertThrows(IllegalArgumentException.class, () -> validator.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> validator.resolve("   "));
    }

    @Test
    void rejectsFragment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator(host -> List.of(address("8.8.8.8")))
                        .resolve("https://api.example.com/x#frag"));
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator(host -> List.of(address("8.8.8.8"))).resolve("https:///path"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "10.0.0.1",
                "172.16.0.1",
                "192.168.1.1",
                "169.254.1.1",
                "fc00::1",
                "::1"
            })
    void rejectsBlockedAddress(String ip) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator(host -> List.of(address(ip))).resolve("https://api.example.com"));
    }

    @Test
    void rejectsWhenAnyResolvedAddressIsBlocked() {
        var validator =
                validator(host -> List.of(address("8.8.8.8"), address("10.0.0.1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.resolve("https://api.example.com"));
    }

    @Test
    void rejectsEmptyResolution() {
        var validator = validator(host -> List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.resolve("https://api.example.com"));
    }

    @Test
    void rejectsResolverFailure() {
        var validator =
                validator(
                        host -> {
                            throw new java.net.UnknownHostException(host);
                        });
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.resolve("https://api.example.com"));
    }

    @Test
    void wrapsUriSyntaxFailure() {
        OutboundUrlValidator validator = validator(host -> List.of(address("8.8.8.8")));
        // "https://[invalid" 缺少右括号，URI 构造抛 URISyntaxException，被 validator 包装。
        assertThrows(IllegalArgumentException.class,
                () -> validator.resolve("https://[invalid"));
    }
}
