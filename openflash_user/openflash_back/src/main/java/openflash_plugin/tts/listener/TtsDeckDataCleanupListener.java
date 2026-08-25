package openflash_plugin.tts.listener;

import java.util.function.ToIntFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import openflash_core.spi.DeckDeletedEvent;
import openflash_plugin.tts.mapper.TtsDeckSettingsMapper;

/** 卡包删除后清理该卡包的 TTS 设置数据。 */
@Component
public class TtsDeckDataCleanupListener {

    private final ToIntFunction<Long> deleteByDeckId;

    @Autowired
    public TtsDeckDataCleanupListener(TtsDeckSettingsMapper mapper) {
        this(mapper::deleteByDeckId);
    }

    /** 供单测注入。 */
    TtsDeckDataCleanupListener(ToIntFunction<Long> deleteByDeckId) {
        this.deleteByDeckId = deleteByDeckId;
    }

    /** 监听卡包删除事件，无条件清自己的卡包级数据。 */
    @EventListener
    public void onDeckDeleted(DeckDeletedEvent event) {
        deleteByDeckId.applyAsInt(event.deckId());
    }
}
