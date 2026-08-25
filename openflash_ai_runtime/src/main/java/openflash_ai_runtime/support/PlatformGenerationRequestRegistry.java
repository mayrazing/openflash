package openflash_ai_runtime.support;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import openflash_ai_runtime.common.RuntimeErrorCode;
import org.springframework.stereotype.Component;

/** 为所有平台生成入口保存唯一、不可逆的取消状态. */
@Component
public class PlatformGenerationRequestRegistry {

    private final ConcurrentHashMap<UUID, RequestState> requests = new ConcurrentHashMap<>();

    /** 在任何目录、密钥、DNS 或模型读取之前占用 requestId. */
    public RequestState reserve(UUID requestId) {
        if (requestId == null) throw invalidRequest();
        RequestState state = new RequestState(requestId);
        if (requests.putIfAbsent(requestId, state) != null) throw invalidRequest();
        return state;
    }

    /** 原子绑定实际执行句柄; 已取消的请求立即取消句柄且不得执行. */
    public boolean bind(RequestState state, Cancelable handle) {
        if (state == null || handle == null || requests.get(state.requestId()) != state) {
            if (handle != null) handle.cancel();
            return false;
        }
        return state.bind(handle);
    }

    /** 将请求永久标记为取消, 并在锁外取消当前精确句柄. */
    public boolean cancel(UUID requestId) {
        if (requestId == null) return false;
        RequestState state = requests.get(requestId);
        return state != null && state.cancel();
    }

    /** 只移除当前调用持有的精确状态, 不误删复用 requestId 的新请求. */
    public void complete(RequestState state) {
        if (state == null) return;
        state.complete();
        requests.remove(state.requestId(), state);
    }

    @FunctionalInterface
    public interface Cancelable {
        void cancel();
    }

    public static final class RequestState {
        private final UUID requestId;
        private Cancelable handle;
        private boolean cancelled;
        private boolean completed;

        private RequestState(UUID requestId) {
            this.requestId = requestId;
        }

        public UUID requestId() {
            return requestId;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        private boolean bind(Cancelable newHandle) {
            synchronized (this) {
                if (!completed && !cancelled && handle == null) {
                    handle = newHandle;
                    return true;
                }
            }
            newHandle.cancel();
            return false;
        }

        private boolean cancel() {
            Cancelable toCancel;
            synchronized (this) {
                if (completed) return false;
                if (cancelled) return true;
                cancelled = true;
                toCancel = handle;
                handle = null;
            }
            if (toCancel != null) toCancel.cancel();
            return true;
        }

        private synchronized void complete() {
            completed = true;
            handle = null;
        }
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }
}
