package openflash_ai_runtime.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/** 解析 runtime 出站 URL, 只允许普通公网单播目标. */
@Component
public class OutboundUrlValidator {

    private static final List<Ipv6Subnet> IANA_ALLOCATED_GLOBAL_UNICAST_V6 = List.of(
            subnet(23, 0x20, 0x01, 0x02), // 2001:200::/23
            subnet(23, 0x20, 0x01, 0x04), // 2001:400::/23
            subnet(23, 0x20, 0x01, 0x06), // 2001:600::/23
            subnet(22, 0x20, 0x01, 0x08), // 2001:800::/22
            subnet(23, 0x20, 0x01, 0x0c), // 2001:c00::/23
            subnet(23, 0x20, 0x01, 0x0e), // 2001:e00::/23
            subnet(23, 0x20, 0x01, 0x12), // 2001:1200::/23
            subnet(22, 0x20, 0x01, 0x14), // 2001:1400::/22
            subnet(23, 0x20, 0x01, 0x18), // 2001:1800::/23
            subnet(23, 0x20, 0x01, 0x1a), // 2001:1a00::/23
            subnet(22, 0x20, 0x01, 0x1c), // 2001:1c00::/22
            subnet(19, 0x20, 0x01, 0x20), // 2001:2000::/19
            subnet(23, 0x20, 0x01, 0x40), // 2001:4000::/23
            subnet(23, 0x20, 0x01, 0x42), // 2001:4200::/23
            subnet(23, 0x20, 0x01, 0x44), // 2001:4400::/23
            subnet(23, 0x20, 0x01, 0x46), // 2001:4600::/23
            subnet(23, 0x20, 0x01, 0x48), // 2001:4800::/23
            subnet(23, 0x20, 0x01, 0x4a), // 2001:4a00::/23
            subnet(23, 0x20, 0x01, 0x4c), // 2001:4c00::/23
            subnet(20, 0x20, 0x01, 0x50), // 2001:5000::/20
            subnet(19, 0x20, 0x01, 0x80), // 2001:8000::/19
            subnet(20, 0x20, 0x01, 0xa0), // 2001:a000::/20
            subnet(20, 0x20, 0x01, 0xb0), // 2001:b000::/20
            subnet(18, 0x20, 0x03, 0x00), // 2003::/18
            subnet(12, 0x24, 0x00), // 2400::/12
            subnet(12, 0x24, 0x10), // 2410::/12
            subnet(12, 0x26, 0x00), // 2600::/12
            subnet(23, 0x26, 0x10, 0x00), // 2610::/23
            subnet(23, 0x26, 0x20, 0x00), // 2620::/23
            subnet(12, 0x26, 0x30), // 2630::/12
            subnet(12, 0x28, 0x00), // 2800::/12
            subnet(12, 0x2a, 0x00), // 2a00::/12
            subnet(12, 0x2a, 0x10), // 2a10::/12
            subnet(12, 0x2c, 0x00)); // 2c00::/12

    private final AddressResolver resolver;

    public OutboundUrlValidator() {
        this(host -> List.of(InetAddress.getAllByName(host)));
    }

    /** 允许测试注入确定性 DNS; 生产仍使用无参构造器的系统解析. */
    public OutboundUrlValidator(AddressResolver resolver) {
        this.resolver = resolver;
    }

    /** 校验 scheme、URL 身份信息和每一个解析地址. */
    public ResolvedTarget resolve(String rawUrl) {
        try {
            URI uri = new URI(rawUrl == null ? "" : rawUrl.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || !validExplicitPort(uri)) {
                throw new IllegalArgumentException("invalid outbound URL");
            }
            List<InetAddress> addresses = List.copyOf(resolver.resolve(uri.getHost()));
            if (addresses.isEmpty()
                    || addresses.stream().anyMatch(address -> !isPublicUnicast(address))) {
                throw new IllegalArgumentException("blocked outbound address");
            }
            return new ResolvedTarget(uri, addresses);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("cannot resolve outbound URL");
        }
    }

    private static boolean validExplicitPort(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null) return false;
        int separator;
        if (authority.startsWith("[")) {
            int bracket = authority.indexOf(']');
            if (bracket < 0) return false;
            if (bracket == authority.length() - 1) return true;
            if (authority.charAt(bracket + 1) != ':') return false;
            separator = bracket + 1;
        } else {
            separator = authority.lastIndexOf(':');
            if (separator < 0) return true;
        }
        String rawPort = authority.substring(separator + 1);
        if (rawPort.isEmpty()) return false;
        try {
            int port = Integer.parseInt(rawPort);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    /** 只接受普通公网单播; IANA special-use 和未分配 IPv6 空间均拒绝. */
    private static boolean isPublicUnicast(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return !isSpecialUseV4(bytes, 0);
        if (bytes.length != 16) return false;
        if (isIpv4Mapped(bytes)) return !isSpecialUseV4(bytes, 12);
        if (isIpv4Compatible(bytes)) return false;
        if (isSpecialUseV6(bytes)) return false;
        return isIanaAllocatedGlobalUnicastV6(bytes);
    }

    private static boolean isIpv4Compatible(byte[] bytes) {
        for (int index = 0; index < 12; index++) {
            if (bytes[index] != 0) return false;
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) return false;
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isSpecialUseV4(byte[] bytes, int offset) {
        return inSubnet(bytes, offset, 8, 0)
                || inSubnet(bytes, offset, 8, 10)
                || inSubnet(bytes, offset, 10, 100, 64)
                || inSubnet(bytes, offset, 8, 127)
                || inSubnet(bytes, offset, 16, 169, 254)
                || inSubnet(bytes, offset, 12, 172, 16)
                || inSubnet(bytes, offset, 24, 192, 0, 0)
                || inSubnet(bytes, offset, 24, 192, 0, 2)
                || inSubnet(bytes, offset, 24, 192, 31, 196)
                || inSubnet(bytes, offset, 24, 192, 52, 193)
                || inSubnet(bytes, offset, 24, 192, 88, 99)
                || inSubnet(bytes, offset, 16, 192, 168)
                || inSubnet(bytes, offset, 24, 192, 175, 48)
                || inSubnet(bytes, offset, 15, 198, 18)
                || inSubnet(bytes, offset, 24, 198, 51, 100)
                || inSubnet(bytes, offset, 24, 203, 0, 113)
                || inSubnet(bytes, offset, 4, 224)
                || inSubnet(bytes, offset, 4, 240);
    }

    private static boolean isSpecialUseV6(byte[] bytes) {
        return inSubnet(bytes, 0, 23, 0x20, 0x01, 0x00)
                || inSubnet(bytes, 0, 32, 0x20, 0x01, 0x0d, 0xb8)
                || inSubnet(bytes, 0, 16, 0x20, 0x02)
                || inSubnet(bytes, 0, 48, 0x26, 0x20, 0x00, 0x4f, 0x80, 0x00)
                || inSubnet(bytes, 0, 20, 0x3f, 0xff, 0x00);
    }

    private static boolean isIanaAllocatedGlobalUnicastV6(byte[] bytes) {
        return IANA_ALLOCATED_GLOBAL_UNICAST_V6.stream()
                .anyMatch(subnet -> inSubnet(
                        bytes, 0, subnet.prefixLength(), subnet.networkBytes()));
    }

    private static Ipv6Subnet subnet(int prefixLength, int... networkBytes) {
        return new Ipv6Subnet(prefixLength, networkBytes);
    }

    private static boolean inSubnet(
            byte[] address,
            int offset,
            int prefixLength,
            int... networkBytes) {
        int fullBytes = prefixLength / 8;
        for (int index = 0; index < fullBytes; index++) {
            if ((address[offset + index] & 255) != networkBytes[index]) return false;
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) return true;
        int mask = 0xff << (8 - remainingBits);
        return ((address[offset + fullBytes] & 255) & mask)
                == (networkBytes[fullBytes] & mask);
    }

    @FunctionalInterface
    public interface AddressResolver {
        List<InetAddress> resolve(String host) throws Exception;
    }

    private record Ipv6Subnet(int prefixLength, int[] networkBytes) {}

    public record ResolvedTarget(URI uri, List<InetAddress> addresses) {
        public ResolvedTarget {
            addresses = List.copyOf(addresses);
        }
    }
}
