package openflash_plugin.ai_card.listener;

import openflash_core.spi.UserDeletedEvent;
import openflash_plugin.ai_card.mapper.AiCardUserTaskCleanupMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 用户永久删除时清理 AI Card 插件的用户异步任务。 */
@Component
public class AiCardUserDataCleanupListener {

    private final AiCardUserTaskCleanupMapper mapper;

    public AiCardUserDataCleanupListener(AiCardUserTaskCleanupMapper mapper) {
        this.mapper = mapper;
    }

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        mapper.deleteByUserId(event.userId());
    }
}
