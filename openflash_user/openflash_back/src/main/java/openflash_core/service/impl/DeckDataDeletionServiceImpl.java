package openflash_core.service.impl;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.PracticeSessionStoreMapper;
import openflash_core.spi.DeckDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 删除一个用户卡包的核心 DB 数据并发布删除事件，不触碰磁盘文件。 */
@Service
public class DeckDataDeletionServiceImpl {

    private final CardProgressMapper cardProgressMapper;
    private final CardMediaMapper cardMediaMapper;
    private final CardMapper cardMapper;
    private final DeckSettingsMapper deckSettingsMapper;
    private final PracticeSessionStoreMapper practiceSessionStoreMapper;
    private final DeckMapper deckMapper;
    private final ApplicationEventPublisher eventPublisher;

    public DeckDataDeletionServiceImpl(
        CardProgressMapper cardProgressMapper,
        CardMediaMapper cardMediaMapper,
        CardMapper cardMapper,
        DeckSettingsMapper deckSettingsMapper,
        PracticeSessionStoreMapper practiceSessionStoreMapper,
        DeckMapper deckMapper,
        ApplicationEventPublisher eventPublisher
    ) {
        this.cardProgressMapper = cardProgressMapper;
        this.cardMediaMapper = cardMediaMapper;
        this.cardMapper = cardMapper;
        this.deckSettingsMapper = deckSettingsMapper;
        this.practiceSessionStoreMapper = practiceSessionStoreMapper;
        this.deckMapper = deckMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void deleteOwnedDeck(Long userId, Long deckId) {
        cardProgressMapper.deleteByDeckId(deckId);
        cardMediaMapper.deleteByDeckId(deckId);
        cardMapper.deleteByDeckId(deckId);
        deckSettingsMapper.deleteByDeckId(deckId);
        practiceSessionStoreMapper.deleteByDeckId(deckId);
        if (deckMapper.deleteById(deckId, userId) != 1) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
        eventPublisher.publishEvent(new DeckDeletedEvent(userId, deckId));
    }
}
