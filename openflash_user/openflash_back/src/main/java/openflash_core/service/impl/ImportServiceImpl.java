package openflash_core.service.impl;

import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.B_TO_A;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import openflash_core.entity.Card;
import openflash_core.entity.CardMedia;
import openflash_core.entity.CardProgress;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckSettings;
import openflash_core.entity.ImportResult;
import openflash_core.entity.UserSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.UserSettingsMapper;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.spi.CardChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckService;
import openflash_core.service.DeckSettingsService;
import openflash_core.service.ImportService;
import openflash_core.service.SystemConfigService;
import openflash_core.service.UserUploadAccessGuard;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 负责接收备份 zip，并把数据恢复到数据库。
 */
@Service
public class ImportServiceImpl implements ImportService {

    private static final long DEFAULT_MAX_ZIP_ENTRY_BYTES = 50L * 1024 * 1024;
    private static final long DEFAULT_MAX_ZIP_TOTAL_BYTES = 100L * 1024 * 1024;
    private static final int DEFAULT_MAX_ZIP_ENTRIES = 100;

    private final CurrentUserService currentUserService;
    private final DeckService deckService;
    private final DeckMapper deckMapper;
    private final CardMapper cardMapper;
    private final CardMediaMapper cardMediaMapper;
    private final CardProgressMapper cardProgressMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final DeckSettingsMapper deckSettingsMapper;
    private final DeckSettingsService deckSettingsService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserUploadAccessGuard userUploadAccessGuard;
    private final SystemConfigService systemConfigService;

    @Autowired
    public ImportServiceImpl(
        CurrentUserService currentUserService,
        DeckService deckService,
        DeckMapper deckMapper,
        CardMapper cardMapper,
        CardMediaMapper cardMediaMapper,
        CardProgressMapper cardProgressMapper,
        UserSettingsMapper userSettingsMapper,
        DeckSettingsMapper deckSettingsMapper,
        DeckSettingsService deckSettingsService,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        UserUploadAccessGuard userUploadAccessGuard,
        SystemConfigService systemConfigService
    ) {
        this.currentUserService = currentUserService;
        this.deckService = deckService;
        this.deckMapper = deckMapper;
        this.cardMapper = cardMapper;
        this.cardMediaMapper = cardMediaMapper;
        this.cardProgressMapper = cardProgressMapper;
        this.userSettingsMapper = userSettingsMapper;
        this.deckSettingsMapper = deckSettingsMapper;
        this.deckSettingsService = deckSettingsService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.userUploadAccessGuard = userUploadAccessGuard;
        this.systemConfigService = systemConfigService;
    }

    ImportServiceImpl(
        CurrentUserService currentUserService,
        DeckService deckService,
        DeckMapper deckMapper,
        CardMapper cardMapper,
        CardMediaMapper cardMediaMapper,
        CardProgressMapper cardProgressMapper,
        UserSettingsMapper userSettingsMapper,
        DeckSettingsMapper deckSettingsMapper,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        UserUploadAccessGuard userUploadAccessGuard,
        SystemConfigService systemConfigService
    ) {
        this(currentUserService, deckService, deckMapper, cardMapper, cardMediaMapper,
            cardProgressMapper, userSettingsMapper, deckSettingsMapper,
            new DeckSettingsServiceImpl(null, null, null), objectMapper,
            eventPublisher, userUploadAccessGuard, systemConfigService);
    }

    /**
     * 导入当前备份 zip，按整包恢复的方式写入数据库。
     */
    @Override
    @Transactional
    public ImportResult importBackupZip(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.IMPORT_BACKUP_BLANK);
        }

        Long userId = currentUserService.getCurrentUserId();
        Map<String, String> zipContent = readZipContent(file.getInputStream());
        String settingsJson = zipContent.get("settings.json");
        String cardsJson = zipContent.get("cards.json");

        if (settingsJson == null && cardsJson == null) {
            throw new AppException(ErrorCode.IMPORT_BACKUP_NO_DATA);
        }

        JsonNode cardsRoot = cardsJson == null ? null : objectMapper.readTree(cardsJson);

        if (cardsRoot != null) {
            requireOwnedMedia(userId, cardsRoot.path("cards"));
            replaceDeckAndCardData(userId, cardsRoot);
        }
        if (settingsJson != null) {
            importSettings(userId, objectMapper.readTree(settingsJson));
        }

        ImportResult result = new ImportResult();
        result.setDeckCount(cardsRoot == null ? 0 : cardsRoot.path("decks").size());
        result.setCardCount(cardsRoot == null ? 0 : cardsRoot.path("cards").size());
        result.setSettingsImported(settingsJson != null);
        return result;
    }

    /**
     * 导入卡包 zip（合并模式，不清空现有数据）。
     */
    @Override
    @Transactional
    public ImportResult importDeckZip(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.IMPORT_DECK_FILE_BLANK);
        }

        Long userId = currentUserService.getCurrentUserId();
        Map<String, String> zipContent = readZipContent(file.getInputStream());
        String decksJson = zipContent.get("decks.json");

        if (decksJson == null) {
            throw new AppException(ErrorCode.IMPORT_DECK_FILE_MISSING_DECKS_JSON);
        }

        JsonNode root = objectMapper.readTree(decksJson);
        requireOwnedMedia(userId, root.path("cards"));
        Map<String, Long> deckIdMap = importDecks(userId, root.path("decks"));
        List<Long> importedCardIds = importCards(userId, deckIdMap, root.path("cards"));
        publishCardChange(importedCardIds, userId, CardChangeEvent.Kind.IMPORTED);

        ImportResult result = new ImportResult();
        result.setDeckCount(root.path("decks").size());
        result.setCardCount(importedCardIds.size());
        result.setSettingsImported(decksContainSettings(root.path("decks")));
        return result;
    }

    /**
     * 读取 zip 里的文件内容。
     */
    private Map<String, String> readZipContent(InputStream inputStream) throws IOException {
        Map<String, String> content = new HashMap<>();
        long maxEntryBytes = positiveLongConfig("import.zip.max-entry-bytes", DEFAULT_MAX_ZIP_ENTRY_BYTES);
        long maxTotalBytes = positiveLongConfig("import.zip.max-total-bytes", DEFAULT_MAX_ZIP_TOTAL_BYTES);
        int maxEntries = positiveIntConfig("import.zip.max-entries", DEFAULT_MAX_ZIP_ENTRIES);
        long[] totalBytes = {0L};
        int entryCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new AppException(ErrorCode.IMPORT_ZIP_LIMIT_EXCEEDED);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                boolean recognized = "settings.json".equals(name)
                        || "cards.json".equals(name)
                        || "decks.json".equals(name);
                String entryContent = readEntry(
                        zipInputStream, maxEntryBytes, maxTotalBytes, totalBytes, recognized);
                if (recognized) {
                    content.put(name, entryContent);
                }
            }
        }
        return content;
    }

    /**
     * 读取 zip 当前条目的文本内容。
     */
    private String readEntry(
            ZipInputStream zipInputStream,
            long maxEntryBytes,
            long maxTotalBytes,
            long[] totalBytes,
            boolean retainContent) throws IOException {
        ByteArrayOutputStream outputStream = retainContent ? new ByteArrayOutputStream() : null;
        byte[] buffer = new byte[4096];
        long entryBytes = 0L;
        int len;
        while ((len = zipInputStream.read(buffer)) != -1) {
            entryBytes += len;
            totalBytes[0] += len;
            if (entryBytes > maxEntryBytes || totalBytes[0] > maxTotalBytes) {
                throw new AppException(ErrorCode.IMPORT_ZIP_LIMIT_EXCEEDED);
            }
            if (outputStream != null) {
                outputStream.write(buffer, 0, len);
            }
        }
        return outputStream == null ? null : outputStream.toString(StandardCharsets.UTF_8);
    }

    /** 读取正长整数限制, DB 缺失或配置非正数时使用安全默认值. */
    private long positiveLongConfig(String key, long defaultValue) {
        long configured = systemConfigService.getLong(key, defaultValue);
        return configured > 0 ? configured : defaultValue;
    }

    /** 读取正整数限制, DB 缺失或配置非正数时使用安全默认值. */
    private int positiveIntConfig(String key, int defaultValue) {
        int configured = systemConfigService.getInt(key, defaultValue);
        return configured > 0 ? configured : defaultValue;
    }

    /**
     * 清空当前用户的卡包和卡片，再按备份内容重建。
     */
    private void replaceDeckAndCardData(Long userId, JsonNode root) {
        clearCurrentDeckData();

        Map<String, Long> deckIdMap = importDecks(userId, root.path("decks"));
        List<Long> importedCardIds = importCards(userId, deckIdMap, root.path("cards"));
        publishCardChange(importedCardIds, userId, CardChangeEvent.Kind.IMPORTED);
    }

    /**
     * 清理当前用户已有的卡包数据。
     */
    private void clearCurrentDeckData() {
        List<Deck> decks = deckService.listDecks();
        for (Deck deck : decks) {
            deckService.deleteDeck(deck.getId());
        }
    }

    /**
     * 导入卡包，并建立旧 ID 到新 ID 的映射。
     */
    private Map<String, Long> importDecks(Long userId, JsonNode decksNode) {
        Map<String, Long> deckIdMap = new HashMap<>();
        if (!decksNode.isArray()) {
            return deckIdMap;
        }

        for (JsonNode deckNode : decksNode) {
            String oldDeckId = textValue(deckNode, "id");
            String name = textValue(deckNode, "name");
            if (oldDeckId == null || name == null || name.trim().isEmpty()) {
                continue;
            }

            Deck deck = new Deck();
            deck.setUserId(userId);
            deck.setName(name.trim());
            deckMapper.insert(deck);
            deckSettingsMapper.insert(deckSettingsForImport(deck.getId(), deckNode.path("settings")));
            deckIdMap.put(oldDeckId, deck.getId());
        }
        return deckIdMap;
    }

    /**
     * 导入卡包设置；旧导出没有 settings 字段时，保留当前默认体验。
     */
    private DeckSettings deckSettingsForImport(Long deckId, JsonNode settingsNode) {
        DeckSettings settings = deckSettingsService.createDefaultSettingsForInsert(deckId);
        if (settingsNode == null || settingsNode.isMissingNode() || settingsNode.isNull()) {
            return settings;
        }
        settings.setNewCardsPerDay(intValue(settingsNode, "newCardsPerDay", settings.getNewCardsPerDay()));
        settings.setTargetRetention(decimalValue(settingsNode, "targetRetention", settings.getTargetRetention()));
        settings.setReviewLoadProfile(defaultText(settingsNode, "reviewLoadProfile", settings.getReviewLoadProfile()));
        settings.setDuplicateSideAEnabled(boolValue(settingsNode, "duplicateSideAEnabled", settings.getDuplicateSideAEnabled()));
        settings.setDuplicateSideBEnabled(boolValue(settingsNode, "duplicateSideBEnabled", settings.getDuplicateSideBEnabled()));
        return deckSettingsService.normalizeSettings(settings);
    }

    /**
     * 判断导入摘要是否应提示卡包设置也随卡包一起导入。
     */
    private boolean decksContainSettings(JsonNode decksNode) {
        if (!decksNode.isArray()) {
            return false;
        }
        for (JsonNode deckNode : decksNode) {
            JsonNode settingsNode = deckNode.path("settings");
            if (!settingsNode.isMissingNode() && !settingsNode.isNull()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 导入卡片、图片和学习进度。
     */
    private List<Long> importCards(Long userId, Map<String, Long> deckIdMap, JsonNode cardsNode) {
        List<Long> importedCardIds = new ArrayList<>();
        if (!cardsNode.isArray()) {
            return importedCardIds;
        }

        for (JsonNode cardNode : cardsNode) {
            String oldDeckId = textValue(cardNode, "deckId");
            Long newDeckId = deckIdMap.get(oldDeckId);
            if (newDeckId == null) {
                continue;
            }

            Card card = new Card();
            card.setDeckId(newDeckId);
            card.setSideA(defaultText(cardNode, "sideA"));
            card.setSideB(defaultText(cardNode, "sideB"));
            cardMapper.insert(card);
            importedCardIds.add(card.getId());

            insertMedia(card.getId(), "A", cardNode.path("sideAImage"));
            insertMedia(card.getId(), "B", cardNode.path("sideBImage"));
            insertProgress(userId, card.getId(), cardNode);
        }
        return importedCardIds;
    }

    /** 预扫描全部导入卡片媒体，确保任何写入或清理前完成归属校验。 */
    private void requireOwnedMedia(Long userId, JsonNode cardsNode) {
        List<String> mediaUrls = new ArrayList<>();
        if (cardsNode.isArray()) {
            for (JsonNode cardNode : cardsNode) {
                collectMediaUrls(cardNode.path("sideAImage"), mediaUrls);
                collectMediaUrls(cardNode.path("sideBImage"), mediaUrls);
            }
        }
        userUploadAccessGuard.requireMediaUrlsOwnedBy(userId, mediaUrls);
    }

    private void collectMediaUrls(JsonNode imageNode, List<String> mediaUrls) {
        if (!imageNode.isArray()) {
            return;
        }
        for (JsonNode node : imageNode) {
            String mediaUrl = node.stringValue();
            if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
                mediaUrls.add(mediaUrl.trim());
            }
        }
    }

    /**
     * 导入某一面的图片列表。
     */
    private void insertMedia(Long cardId, String side, JsonNode imageNode) {
        if (!imageNode.isArray()) {
            return;
        }

        int sortOrder = 0;
        for (JsonNode node : imageNode) {
            String mediaUrl = node.stringValue();
            if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                continue;
            }

            CardMedia media = new CardMedia();
            media.setCardId(cardId);
            media.setCardSide(side);
            media.setMediaUrl(mediaUrl.trim());
            media.setSortOrder(sortOrder++);
            cardMediaMapper.insert(media);
        }
    }

    /**
     * 导入卡片学习进度。
     */
    private void insertProgress(Long userId, Long cardId, JsonNode cardNode) {
        JsonNode directionProgressesNode = cardNode.path("directionProgresses");
        if (!directionProgressesNode.isMissingNode() && !directionProgressesNode.isNull()) {
            insertDirectionProgress(userId, cardId, A_TO_B, directionProgressesNode.path("a2b"), cardNode);
            insertDirectionProgress(userId, cardId, B_TO_A, directionProgressesNode.path("b2a"), cardNode);
            return;
        }

        JsonNode fsrsNode = cardNode.path("fsrs");
        CardProgress a2bProgress = buildLegacyProgress(userId, cardId, A_TO_B, cardNode, fsrsNode);
        CardProgress b2aProgress = buildLegacyProgress(userId, cardId, B_TO_A, cardNode, fsrsNode);
        cardProgressMapper.insert(a2bProgress);
        cardProgressMapper.insert(b2aProgress);
    }

    private void insertDirectionProgress(Long userId, Long cardId, String direction, JsonNode directionNode, JsonNode cardNode) {
        CardProgress progress = new CardProgress();
        progress.setUserId(userId);
        progress.setCardId(cardId);
        progress.setDirection(direction);
        progress.setState(defaultText(directionNode, "state", defaultText(cardNode, "state", "new")));
        progress.setStep(intValue(directionNode.path("fsrs"), "step", null));
        progress.setStability(doubleValue(directionNode.path("fsrs"), "stability", 0.0));
        progress.setDifficulty(doubleValue(directionNode.path("fsrs"), "difficulty", 0.0));
        progress.setNextReviewDate(dateValue(directionNode.path("fsrs"), "nextReviewDate", LocalDate.now()));
        progress.setLastReviewDate(dateValue(directionNode.path("fsrs"), "lastReviewDate", null));
        progress.setReps(intValue(directionNode.path("fsrs"), "reps", 0));
        progress.setLapses(intValue(directionNode.path("fsrs"), "lapses", 0));
        progress.setLastRating(intValue(directionNode.path("fsrs"), "lastRating", 0));
        progress.setFirstLearnedDate(dateValue(directionNode, "firstLearnedDate", dateValue(cardNode, "firstLearnedDate", null)));
        progress.setMasteredAt(dateTimeValue(directionNode, "masteredAt"));
        cardProgressMapper.insert(progress);
    }

    private CardProgress buildLegacyProgress(Long userId, Long cardId, String direction, JsonNode cardNode, JsonNode fsrsNode) {
        CardProgress progress = new CardProgress();
        progress.setUserId(userId);
        progress.setCardId(cardId);
        progress.setDirection(direction);
        progress.setState(defaultText(cardNode, "state", "new"));
        progress.setStep(intValue(fsrsNode, "step", null));
        progress.setStability(doubleValue(fsrsNode, "stability", 0.0));
        progress.setDifficulty(doubleValue(fsrsNode, "difficulty", 0.0));
        progress.setNextReviewDate(dateValue(fsrsNode, "nextReviewDate", LocalDate.now()));
        progress.setLastReviewDate(dateValue(fsrsNode, "lastReviewDate", null));
        progress.setReps(intValue(fsrsNode, "reps", 0));
        progress.setLapses(intValue(fsrsNode, "lapses", 0));
        progress.setLastRating(intValue(fsrsNode, "lastRating", 0));
        progress.setFirstLearnedDate(dateValue(cardNode, "firstLearnedDate", null));
        progress.setMasteredAt(dateTimeValue(cardNode, "masteredAt"));
        return progress;
    }

    /** 通知插件卡片发生变化，插件自行决定触发哪些后台任务。 */
    private void publishCardChange(List<Long> cardIds, Long userId, CardChangeEvent.Kind kind) {
        if (cardIds == null || cardIds.isEmpty()) {
            return;
        }
        CardChangeEvent event = CardChangeEvent.of(userId, cardIds, kind);
        eventPublisher.publishEvent(event);
    }

    /**
     * 导入用户设置。
     */
    private void importSettings(Long userId, JsonNode settingsNode) {
        UserSettings settings = userSettingsMapper.findByUserId(userId);
        if (settings == null) {
            throw new AppException(ErrorCode.USER_SETTINGS_NOT_FOUND);
        }

        settings.setTheme(defaultText(settingsNode, "theme", settings.getTheme()));
        settings.setSoundEnabled(boolValue(settingsNode, "soundEnabled",
            settings.getSoundEnabled() == null ? Boolean.TRUE : settings.getSoundEnabled()));
        userSettingsMapper.update(settings);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode valueNode = node.path(field);
        return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asString();
    }

    private String defaultText(JsonNode node, String field) {
        return defaultText(node, field, "");
    }

    private String defaultText(JsonNode node, String field, String defaultValue) {
        String value = textValue(node, field);
        return value == null ? defaultValue : value;
    }

    private Integer intValue(JsonNode node, String field, Integer defaultValue) {
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return defaultValue;
        }
        return defaultValue == null ? valueNode.asInt() : valueNode.asInt(defaultValue);
    }

    private Double doubleValue(JsonNode node, String field, Double defaultValue) {
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return defaultValue;
        }
        return defaultValue == null ? valueNode.asDouble() : valueNode.asDouble(defaultValue);
    }

    private BigDecimal decimalValue(JsonNode node, String field, BigDecimal defaultValue) {
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.decimalValue();
    }

    private Boolean boolValue(JsonNode node, String field, Boolean defaultValue) {
        JsonNode valueNode = node.path(field);
        return valueNode.isMissingNode() || valueNode.isNull() ? defaultValue : valueNode.asBoolean(defaultValue);
    }

    private LocalDate dateValue(JsonNode node, String field, LocalDate defaultValue) {
        String value = textValue(node, field);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return LocalDate.parse(value);
    }

    private LocalDateTime dateTimeValue(JsonNode node, String field) {
        String value = textValue(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay();
        }
        return LocalDateTime.parse(value);
    }
}
