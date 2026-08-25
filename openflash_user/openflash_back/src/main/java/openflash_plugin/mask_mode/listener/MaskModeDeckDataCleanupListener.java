package openflash_plugin.mask_mode.listener;

import java.util.function.ToIntFunction;
import openflash_core.spi.DeckDeletedEvent;
import openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 卡包删除后清理该卡包的遮蔽模式设置数据。 */
@Component
public class MaskModeDeckDataCleanupListener {

    private final ToIntFunction<Long> deleteByDeckId;

    @Autowired
    public MaskModeDeckDataCleanupListener(MaskModeDeckSettingsMapper mapper) {
        this(mapper::deleteByDeckId);
    }

    /** 供单测注入。 */
    MaskModeDeckDataCleanupListener(ToIntFunction<Long> deleteByDeckId) {
        this.deleteByDeckId = deleteByDeckId;
    }

    /** 监听卡包删除事件，无条件清自己的卡包级数据。 */
    @EventListener
    public void onDeckDeleted(DeckDeletedEvent event) {
        deleteByDeckId.applyAsInt(event.deckId());
    }
}
