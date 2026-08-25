package openflash_core.service;

import java.util.Map;
import openflash_core.common.ErrorCode;
import openflash_core.spi.UserAccountInvalidatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后通知账号失效用户，并关闭其全部 SSE 连接。 */
@Service
public class UserAccountInvalidationNotifier {

    private static final String EVENT_NAME = "account-invalidated";

    private final UserSseRegistry userSseRegistry;

    public UserAccountInvalidationNotifier(UserSseRegistry userSseRegistry) {
        this.userSseRegistry = userSseRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAccountInvalidated(UserAccountInvalidatedEvent event) {
        ErrorCode errorCode = switch (event.reason()) {
            case BANNED -> ErrorCode.ACCOUNT_BANNED;
            case DELETED -> ErrorCode.ACCOUNT_DELETED;
        };
        userSseRegistry.pushAndClose(
            event.userId(),
            EVENT_NAME,
            Map.of("reason", event.reason().name(), "code", errorCode.value())
        );
    }
}
