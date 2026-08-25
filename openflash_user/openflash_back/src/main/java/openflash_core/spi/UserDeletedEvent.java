package openflash_core.spi;

import java.util.Objects;

/** 用户永久删除前发布的同步清理事件。 */
public record UserDeletedEvent(Long userId) {

    public UserDeletedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
