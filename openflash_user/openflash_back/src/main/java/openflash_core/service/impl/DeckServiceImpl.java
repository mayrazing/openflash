package openflash_core.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.entity.CardMedia;
import openflash_core.entity.Deck;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckService;
import openflash_core.service.DeckSettingsService;

@Service
public class DeckServiceImpl implements DeckService {

    private static final List<String> DEFAULT_INSTALLED_PLUGIN_IDS = List.of("tts", "ai-card");

    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;
    private final CardMediaMapper cardMediaMapper;
    private final DeckSettingsMapper deckSettingsMapper;
    private final DeckSettingsService deckSettingsService;
    private final PluginInstallMapper pluginInstallMapper;
    private final UploadFileDeleter uploadFileDeleter;
    private final DeckDataDeletionServiceImpl deckDataDeletionService;

    @Autowired
    public DeckServiceImpl(
        CurrentUserService currentUserService,
        DeckMapper deckMapper,
        CardMediaMapper cardMediaMapper,
        DeckSettingsMapper deckSettingsMapper,
        DeckSettingsService deckSettingsService,
        PluginInstallMapper pluginInstallMapper,
        UploadFileDeleter uploadFileDeleter,
        DeckDataDeletionServiceImpl deckDataDeletionService
    ) {
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
        this.cardMediaMapper = cardMediaMapper;
        this.deckSettingsMapper = deckSettingsMapper;
        this.deckSettingsService = deckSettingsService;
        this.pluginInstallMapper = pluginInstallMapper;
        this.uploadFileDeleter = uploadFileDeleter;
        this.deckDataDeletionService = deckDataDeletionService;
    }

    DeckServiceImpl(
        CurrentUserService currentUserService,
        DeckMapper deckMapper,
        CardMediaMapper cardMediaMapper,
        DeckSettingsMapper deckSettingsMapper,
        PluginInstallMapper pluginInstallMapper,
        UploadFileDeleter uploadFileDeleter,
        DeckDataDeletionServiceImpl deckDataDeletionService
    ) {
        this(currentUserService, deckMapper, cardMediaMapper, deckSettingsMapper,
            new DeckSettingsServiceImpl(null, null, null), pluginInstallMapper,
            uploadFileDeleter, deckDataDeletionService);
    }

    @Override
    public List<Deck> listDecks() {
        Long userId = currentUserService.getCurrentUserId();
        return deckMapper.findByUserId(userId);
    }

    @Override
    public Deck getDeck(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
        return deck;
    }

    @Override
    @Transactional
    public Deck createDeck(String name) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = new Deck();
        deck.setUserId(userId);
        deck.setName(requireName(name));
        deckMapper.insert(deck);
        deckSettingsMapper.insert(deckSettingsService.createDefaultSettingsForInsert(deck.getId()));
        installDefaultPlugins(userId, deck.getId());
        return getDeck(deck.getId());
    }

    @Override
    @Transactional
    public Deck renameDeck(Long deckId, String name) {
        Long userId = currentUserService.getCurrentUserId();
        int updated = deckMapper.updateName(deckId, userId, requireName(name));
        if (updated == 0) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
        return getDeck(deckId);
    }

    @Override
    @Transactional
    public void deleteDeck(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }

        List<CardMedia> oldMedia = cardMediaMapper.findByDeckId(deckId);
        deckDataDeletionService.deleteOwnedDeck(userId, deckId);
        uploadFileDeleter.delete(oldMedia);
    }

    /** 给新增卡包写入默认插件安装关系；功能是否可见仍由全局开关和卡包级设置决定。 */
    private void installDefaultPlugins(Long userId, Long deckId) {
        for (String pluginId : DEFAULT_INSTALLED_PLUGIN_IDS) {
            pluginInstallMapper.insert(userId, deckId, pluginId);
        }
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new AppException(ErrorCode.DECK_NAME_BLANK);
        }
        return name.trim();
    }
}
