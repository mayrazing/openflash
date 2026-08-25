package openflash_plugin.tts.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Deck;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.TypeRegistryMapper;
import openflash_core.service.CurrentUserService;
import openflash_plugin.tts.dto.TtsDeckSettingsUpdateCommand;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.mapper.TtsDeckSettingsMapper;
import openflash_plugin.tts.service.TtsDeckSettingsService;

@Service
public class TtsDeckSettingsServiceImpl implements TtsDeckSettingsService {

    private static final String ENGINE_REGISTRY_TYPE = "tts_engine";
    private static final String DEFAULT_ENGINE = TtsFeatureGuard.ENGINE_COSYVOICE3;
    private static final Set<String> SUPPORTED_ENGINES = Set.of(
        TtsFeatureGuard.ENGINE_COSYVOICE3, TtsFeatureGuard.ENGINE_PIPER);

    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;
    private final TtsDeckSettingsMapper ttsDeckSettingsMapper;
    private final TypeRegistryMapper typeRegistryMapper;

    public TtsDeckSettingsServiceImpl(
            CurrentUserService currentUserService,
            DeckMapper deckMapper,
            TtsDeckSettingsMapper ttsDeckSettingsMapper,
            TypeRegistryMapper typeRegistryMapper) {
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
        this.ttsDeckSettingsMapper = ttsDeckSettingsMapper;
        this.typeRegistryMapper = typeRegistryMapper;
    }

    @Override
    public TtsDeckSettings getForCurrentUser(Long deckId) {
        requireDeckOwnership(deckId);
        TtsDeckSettings settings = ttsDeckSettingsMapper.findByDeckId(deckId);
        return settings == null ? defaultSettings(deckId) : normalizeEngine(settings);
    }

    @Override
    @Transactional
    public TtsDeckSettings saveForCurrentUser(Long deckId, TtsDeckSettingsUpdateCommand command) {
        requireDeckOwnership(deckId);
        requireCompleteSettings(command);
        TtsDeckSettings current = ttsDeckSettingsMapper.findByDeckId(deckId);
        TtsDeckSettings next = new TtsDeckSettings(
            deckId, command.autoSpeakA(), command.autoSpeakB(), command.engine());
        if (current == null) {
            ttsDeckSettingsMapper.insert(next);
        } else {
            ttsDeckSettingsMapper.update(next);
        }
        return next;
    }

    @Override
    public List<String> getEnabledEngines() {
        List<String> registered = typeRegistryMapper.findEnabledItemKeys(ENGINE_REGISTRY_TYPE);
        List<String> supported = registered == null
            ? List.of()
            : registered.stream().filter(SUPPORTED_ENGINES::contains).distinct().toList();
        return supported.isEmpty() ? List.of(DEFAULT_ENGINE) : supported;
    }

    private void requireDeckOwnership(Long deckId) {
        Long userId = currentUserService.getCurrentUserId();
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
    }

    private void requireCompleteSettings(TtsDeckSettingsUpdateCommand command) {
        if (command == null
                || command.autoSpeakA() == null
                || command.autoSpeakB() == null
                || command.engine() == null
                || !getEnabledEngines().contains(command.engine())) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
    }

    private TtsDeckSettings defaultSettings(Long deckId) {
        return new TtsDeckSettings(deckId, false, false, defaultEngine());
    }

    private TtsDeckSettings normalizeEngine(TtsDeckSettings settings) {
        List<String> enabled = getEnabledEngines();
        String engine = enabled.contains(settings.engine()) ? settings.engine() : defaultEngine(enabled);
        return new TtsDeckSettings(
            settings.deckId(), settings.autoSpeakA(), settings.autoSpeakB(), engine);
    }

    private String defaultEngine() {
        return defaultEngine(getEnabledEngines());
    }

    private String defaultEngine(List<String> enabled) {
        return enabled.contains(DEFAULT_ENGINE) ? DEFAULT_ENGINE : enabled.get(0);
    }
}
