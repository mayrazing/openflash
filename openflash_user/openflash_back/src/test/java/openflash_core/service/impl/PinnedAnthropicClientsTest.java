package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import openflash_core.security.OutboundUrlValidator.ResolvedTarget;
import org.junit.jupiter.api.Test;

class PinnedAnthropicClientsTest {

    @Test
    void transportPinsValidatedAddressesAndRejectsRedirectsAndProxies() throws Exception {
        InetAddress approved = InetAddress.getByName("203.0.113.10");
        ResolvedTarget target = new ResolvedTarget(
                URI.create("https://provider.example/v1"), List.of(approved));

        OkHttpClient client = PinnedAnthropicClients.buildHttpClient(
                target, Duration.ofSeconds(3));

        assertEquals(List.of(approved), client.dns().lookup("provider.example"));
        assertEquals(List.of(), client.dns().lookup("other.example"));
        assertEquals(Proxy.NO_PROXY, client.proxy());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
    }
}
