package openflash_core.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.spi.DeckDeletedEvent;

/** 卡包删除后清理该卡包的全部插件安装记录。 */
@Component
public class PluginInstallCleanupListener {

    private final PluginInstallMapper mapper;

    public PluginInstallCleanupListener(PluginInstallMapper mapper) {
        this.mapper = mapper;
    }

    /** 监听卡包删除事件，清掉安装关系。 */
    @EventListener
    public void onDeckDeleted(DeckDeletedEvent event) {
        mapper.deleteByDeckId(event.deckId());
    }
}
