package openflash_core.spi;

import java.util.Objects;

/** 用户账号被封禁或删除后发布的会话失效事件。 */
public record UserAccountInvalidatedEvent(Long userId, Reason reason) {

    public enum Reason {
        BANNED,
        DELETED
    }

    public UserAccountInvalidatedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
