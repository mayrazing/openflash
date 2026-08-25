package openflash_ai_runtime.transport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import openflash_ai_runtime.common.RuntimeErrorCode;
import org.springframework.stereotype.Component;

/** 按受支持 protocol 查找唯一 transport. */
@Component
public class PlatformAiTransportRegistry {

    private final Map<String, PlatformAiTransport> transports;

    public PlatformAiTransportRegistry(List<PlatformAiTransport> transports) {
        Map<String, PlatformAiTransport> registered = new HashMap<>();
        for (PlatformAiTransport transport : transports) {
            if (registered.putIfAbsent(transport.protocol(), transport) != null) {
                throw new IllegalStateException("duplicate platform AI transport protocol");
            }
        }
        this.transports = Map.copyOf(registered);
    }

    public PlatformAiTransport require(String protocol) {
        PlatformAiTransport transport = transports.get(protocol);
        if (transport == null) {
            throw new openflash_ai_runtime.common.RuntimeException(
                    RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
        }
        return transport;
    }

    /** requestId 不含 transport 信息, 因此逐个精确 map 尝试取消. */
    public boolean cancel(UUID requestId) {
        boolean cancelled = false;
        for (PlatformAiTransport transport : transports.values()) {
            cancelled = transport.cancel(requestId) || cancelled;
        }
        return cancelled;
    }
}
