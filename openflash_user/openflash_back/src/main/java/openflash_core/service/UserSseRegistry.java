package openflash_core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class UserSseRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserSseRegistry.class);
    private static final int MAX_EMITTERS_PER_USER = 3;
    private static final String CONNECTED_EVENT = "connected";

    private final Map<Long, Set<SseEmitter>> store = new ConcurrentHashMap<>();

    public void register(Long userId, SseEmitter emitter) {
        List<SseEmitter> staleEmitters = new ArrayList<>();
        store.compute(userId, (key, currentEmitters) -> {
            Set<SseEmitter> emitters = currentEmitters;
            if (emitters == null) {
                emitters = ConcurrentHashMap.newKeySet();
            }
            while (emitters.size() >= MAX_EMITTERS_PER_USER) {
                SseEmitter stale = emitters.iterator().next();
                emitters.remove(stale);
                staleEmitters.add(stale);
            }
            emitters.add(emitter);
            return emitters;
        });
        for (SseEmitter staleEmitter : staleEmitters) {
            complete(userId, staleEmitter);
        }
        try {
            emitter.send(SseEmitter.event().name(CONNECTED_EVENT).data("ok"));
        } catch (IOException | IllegalStateException ex) {
            LOGGER.debug("Failed to establish SSE connection for user {}", userId, ex);
            remove(userId, emitter);
        }
    }

    public void push(Long userId, String eventName, Object payload) {
        Set<SseEmitter> emitters = store.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters.toArray(new SseEmitter[0])) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                LOGGER.debug("Failed to send SSE event to user {}", userId, ex);
                remove(userId, emitter);
            }
        }
    }

    /** 向用户全部当前连接发送最后一条事件，然后关闭并移除这些连接。 */
    public void pushAndClose(Long userId, String eventName, Object payload) {
        Set<SseEmitter> emitters = detach(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters.toArray(new SseEmitter[0])) {
            sendAndClose(userId, emitter, eventName, payload);
        }
    }

    /** 向一个指定连接发送最后一条事件并关闭，只移除该连接。 */
    public void pushAndClose(
        Long userId,
        SseEmitter emitter,
        String eventName,
        Object payload
    ) {
        remove(userId, emitter);
        sendAndClose(userId, emitter, eventName, payload);
    }

    /** 向全部已连接用户发送心跳，防止代理关闭空闲 SSE 连接。 */
    @Scheduled(fixedDelayString = "#{@systemConfigService.getLong('sse.heartbeat-interval-millis', 25000L)}")
    public void sendHeartbeat() {
        for (Long userId : store.keySet()) {
            push(userId, "heartbeat", "ok");
        }
    }

    public void remove(Long userId) {
        detach(userId);
    }

    public void remove(Long userId, SseEmitter emitter) {
        store.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private Set<SseEmitter> detach(Long userId) {
        AtomicReference<Set<SseEmitter>> detached = new AtomicReference<>();
        store.computeIfPresent(userId, (key, emitters) -> {
            detached.set(emitters);
            return null;
        });
        return detached.get();
    }

    private void complete(Long userId, SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ex) {
            LOGGER.debug("Failed to close SSE connection for user {}", userId, ex);
        }
    }

    private void sendAndClose(Long userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException ex) {
            LOGGER.debug("Failed to send final SSE event to user {}", userId, ex);
        } finally {
            complete(userId, emitter);
        }
    }
}
