package openflash_ai_runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundUrlValidatorTest {

    private static final String PROVIDER_URL = "https://api.example.test/v1";

    @Test
    void cgnatRangeUsesExactRfc6598Boundaries() throws Exception {
        assertAllowed("100.63.255.255");
        assertBlocked("100.64.0.0");
        assertBlocked("100.127.255.255");
        assertAllowed("100.128.0.0");
    }

    @Test
    void dnsResolutionToAnyCgnatAddressIsRejectedWithoutHostDisclosure() throws Exception {
        OutboundUrlValidator validator = new OutboundUrlValidator(host -> List.of(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("100.96.0.1")));

        assertThatThrownBy(() -> validator.resolve(PROVIDER_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blocked outbound address")
                .hasMessageNotContaining("api.example.test");
    }

    @Test
    void javaNormalizedAndRawIpv4MappedIpv6CannotBypassCgnatCheck() throws Exception {
        InetAddress normalized = InetAddress.getByName("::ffff:100.64.1.2");
        assertThat(normalized).isInstanceOf(Inet4Address.class);
        assertBlocked(normalized);
        assertBlocked(ipv4Mapped("100.64.1.2"));
    }

    @Test
    void otherClearlyNonPublicSpecialUseRangesAreRejected() throws Exception {
        for (String address : List.of(
                "192.0.2.1",
                "198.18.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "240.0.0.1",
                "255.255.255.255",
                "100::1",
                "2001:db8::1",
                "3fff::1")) {
            assertBlocked(address);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2d00::1", "2e00::1", "3000::1", "3fff::1"})
    void ianaReservedGlobalUnicastSpaceIsRejected(String address) throws Exception {
        assertBlocked(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2001:1000::1",
        "2001:4e00::1",
        "2001:6000::1",
        "2001:c000::1",
        "2003:4000::1",
        "2420::1",
        "2610:200::1",
        "2620:200::1",
        "2640::1",
        "2700::1",
        "2900::1",
        "2a20::1",
        "2b00::1",
        "2c10::1"
    })
    void ianaUnallocatedGapsBetweenAllocatedBlocksAreRejected(String address) throws Exception {
        assertBlocked(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001::1", "2001:db8::1", "2002::1"})
    void allocatedSpecialPurposeBlocksRemainRejected(String address) throws Exception {
        assertBlocked(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2001:200::",
        "2001:3ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:400::",
        "2001:5ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:600::",
        "2001:7ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:800::",
        "2001:bff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:c00::",
        "2001:dff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:e00::",
        "2001:fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:1200::",
        "2001:13ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:1400::",
        "2001:17ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:1800::",
        "2001:19ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:1a00::",
        "2001:1bff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:1c00::",
        "2001:1fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:2000::",
        "2001:3fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4000::",
        "2001:41ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4200::",
        "2001:43ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4400::",
        "2001:45ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4600::",
        "2001:47ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4800::",
        "2001:49ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4a00::",
        "2001:4bff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:4c00::",
        "2001:4dff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:5000::",
        "2001:5fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:8000::",
        "2001:9fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:a000::",
        "2001:afff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2001:b000::",
        "2001:bfff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2003::",
        "2003:3fff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2400::",
        "240f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2410::",
        "241f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2600::",
        "260f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2610::",
        "2610:1ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2620::",
        "2620:1ff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2630::",
        "263f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2800::",
        "280f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2a00::",
        "2a0f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2a10::",
        "2a1f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        "2c00::",
        "2c0f:ffff:ffff:ffff:ffff:ffff:ffff:ffff"
    })
    void ianaAllocatedNormalGlobalUnicastBoundariesRemainAllowed(String address)
            throws Exception {
        assertAllowed(address);
    }

    @Test
    void ordinaryPublicIpv4AndIpv6RemainAllowed() throws Exception {
        for (String address : List.of(
                "1.1.1.1",
                "8.8.8.8",
                "2001:4860:4860::8888",
                "2606:4700:4700::1111")) {
            assertAllowed(address);
        }
    }

    private static void assertAllowed(String address) throws Exception {
        OutboundUrlValidator validator = validatorFor(InetAddress.getByName(address));
        assertThat(validator.resolve(PROVIDER_URL).addresses())
                .as(address)
                .containsExactly(InetAddress.getByName(address));
    }

    private static void assertBlocked(String address) throws Exception {
        assertBlocked(InetAddress.getByName(address));
    }

    private static void assertBlocked(InetAddress address) {
        OutboundUrlValidator validator = validatorFor(address);
        assertThatThrownBy(() -> validator.resolve(PROVIDER_URL))
                .as(address.getHostAddress())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blocked outbound address");
    }

    private static OutboundUrlValidator validatorFor(InetAddress address) {
        return new OutboundUrlValidator(host -> List.of(address));
    }

    private static Inet6Address ipv4Mapped(String embedded) throws Exception {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        byte[] ipv4 = InetAddress.getByName(embedded).getAddress();
        System.arraycopy(ipv4, 0, bytes, 12, ipv4.length);
        return Inet6Address.getByAddress(null, bytes, -1);
    }
}
