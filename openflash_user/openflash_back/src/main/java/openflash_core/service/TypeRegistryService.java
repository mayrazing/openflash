package openflash_core.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.entity.PracticeReviewLoadProfile;
import openflash_core.config.AiProperties;
import openflash_core.entity.PracticeModeOption;
import openflash_core.mapper.TypeRegistryMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 读取类型注册表配置，并用短期内存缓存降低数据库访问次数。
 */
@Service
public class TypeRegistryService {
    static final long CACHE_TTL_MILLIS = 60_000L;
    static final String AI_FEATURE_MAPPING = "ai_feature_mapping";
    static final String AI_PROFILE = "ai_profile";
    static final String AI_PROVIDER_KIND = "ai_provider_kind";
    static final String DEEPSEEK_MODEL = "deepseek_model";
    static final String INTERFACE_LANGUAGE = "interface_language";
    static final String PRACTICE_MODE = "practice_mode";
    static final String REVIEW_LOAD_PROFILE = "review_load_profile";
    private static final Set<String> SUPPORTED_DEEPSEEK_MODELS = Set.of("deepseek-v4-flash", "deepseek-v4-pro");
    private static final Set<String> SUPPORTED_AI_PROVIDER_KINDS = Set.of("codex-cli");
    private static final Set<String> SUPPORTED_LANGUAGE_KEYS = Set.of("zh", "en", "fi", "de");
    private static final Set<String> SUPPORTED_PRACTICE_MODE_KEYS = Set.of("a2b", "b2a", "random");
    private static final List<PracticeModeOption> DEFAULT_PRACTICE_MODES = List.of(
            new PracticeModeOption("a2b", "Side A → Side B"),
            new PracticeModeOption("b2a", "Side B → Side A"),
            new PracticeModeOption("random", "Random")
    );
    private static final List<PracticeModeOption> DEFAULT_REVIEW_LOAD_PROFILES = List.of(
            new PracticeModeOption(PracticeReviewLoadProfile.RELAXED.key(), PracticeReviewLoadProfile.RELAXED.label()),
            new PracticeModeOption(PracticeReviewLoadProfile.STANDARD.key(), PracticeReviewLoadProfile.STANDARD.label()),
            new PracticeModeOption(PracticeReviewLoadProfile.INTENSIVE.key(), PracticeReviewLoadProfile.INTENSIVE.label())
    );
    private static final List<PracticeModeOption> DEFAULT_DEEPSEEK_MODELS = List.of(
            new PracticeModeOption("deepseek-v4-flash", "DeepSeek V4 Flash"),
            new PracticeModeOption("deepseek-v4-pro", "DeepSeek V4 Pro")
    );
    private static final List<PracticeModeOption> DEFAULT_LANGUAGE_OPTIONS = List.of(
            new PracticeModeOption("zh", "中文"),
            new PracticeModeOption("en", "English"),
            new PracticeModeOption("fi", "Suomi"),
            new PracticeModeOption("de", "Deutsch")
    );

    private final BiFunction<String, String, String> configJsonLoader;
    private final Function<String, List<PracticeModeOption>> practiceModesLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedEntry<String>> configJsonCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<List<PracticeModeOption>>> practiceModesCache =
            new ConcurrentHashMap<>();

    /**
     * 使用数据库 mapper 创建类型注册表服务。
     */
    @Autowired
    public TypeRegistryService(TypeRegistryMapper mapper) {
        this(mapper::findConfigJson, mapper::findEnabledItemKeys, mapper::findEnabledPracticeModes);
    }

    /**
     * 使用自定义加载器创建类型注册表服务，供包内测试替换数据库读取。
     */
    TypeRegistryService(
            BiFunction<String, String, String> configJsonLoader,
            Function<String, List<String>> itemKeysLoader) {
        this(
                configJsonLoader,
                itemKeysLoader,
                registryType -> {
                    List<String> keys = itemKeysLoader.apply(registryType);
                    return keys == null
                            ? List.of()
                            : keys.stream()
                                    .map(itemKey -> new PracticeModeOption(itemKey, defaultPracticeModeLabel(itemKey)))
                                    .toList();
                }
        );
    }

    /**
     * 使用自定义加载器创建类型注册表服务，供包内测试替换配置、key 和练习模式读取。
     */
    TypeRegistryService(
            BiFunction<String, String, String> configJsonLoader,
            Function<String, List<String>> itemKeysLoader,
            Function<String, List<PracticeModeOption>> practiceModesLoader) {
        this.configJsonLoader = configJsonLoader;
        this.practiceModesLoader = practiceModesLoader;
    }

    /**
     * 按 AI 功能 key 解析对应的模型 profile，配置缺失或格式错误时返回 null。
     */
    public AiProperties.AiProfile resolveAiProfile(String featureKey) {
        String mappingJson = loadConfigJson(AI_FEATURE_MAPPING, featureKey);
        String profileName = readProfileName(mappingJson);
        if (profileName == null || profileName.isBlank()) {
            return null;
        }

        String profileJson = loadConfigJson(AI_PROFILE, profileName);
        if (profileJson == null || profileJson.isBlank()) {
            return null;
        }

        try {
            AiProperties.AiProfile profile = objectMapper.readValue(profileJson,
                    AiProperties.AiProfile.class);
            profile.setName(profileName);
            return profile;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 读取启用练习模式 key 列表；数据库无结果时返回固定三模式。
     */
    public List<String> getEnabledPracticeModeKeys() {
        return getEnabledPracticeModes().stream()
                .map(PracticeModeOption::getValue)
                .toList();
    }

    /**
     * 读取页面可展示的练习模式；数据库无结果时回退固定三模式。
     */
    public List<PracticeModeOption> getEnabledPracticeModes() {
        List<PracticeModeOption> modes = loadPracticeModes(PRACTICE_MODE);
        return copyPracticeModes(modes.isEmpty() ? DEFAULT_PRACTICE_MODES : modes);
    }

    /**
     * 读取页面可展示的学习强度档位；数据库无结果时回退内置三档。
     */
    public List<PracticeModeOption> getEnabledReviewLoadProfiles() {
        List<PracticeModeOption> profiles = loadReviewLoadProfiles(REVIEW_LOAD_PROFILE);
        return copyPracticeModes(profiles.isEmpty() ? DEFAULT_REVIEW_LOAD_PROFILES : profiles);
    }

    /**
     * 读取设置页可展示的 DeepSeek 模型；数据库无结果时回退内置模型。
     */
    public List<PracticeModeOption> getEnabledDeepSeekModels() {
        List<PracticeModeOption> models = loadDeepSeekModels(DEEPSEEK_MODEL);
        return copyPracticeModes(models.isEmpty() ? DEFAULT_DEEPSEEK_MODELS : models);
    }

    /**
     * 读取数据库启用且后端已实现的 AI provider kind, 数据库无结果时返回空列表.
     */
    public List<String> getEnabledAiProviderKinds() {
        return loadRegisteredOptions(
                AI_PROVIDER_KIND,
                mode -> mode != null && SUPPORTED_AI_PROVIDER_KINDS.contains(mode.getValue()),
                PracticeModeOption::getValue
        ).stream().map(PracticeModeOption::getValue).toList();
    }

    /**
     * 读取设置页可展示的界面语言；数据库无结果时回退内置四种语言。
     */
    public List<PracticeModeOption> getEnabledLanguageOptions() {
        List<PracticeModeOption> languages = loadLanguageOptions(INTERFACE_LANGUAGE);
        return copyPracticeModes(languages.isEmpty() ? DEFAULT_LANGUAGE_OPTIONS : languages);
    }

    /**
     * 读取设置页允许保存的界面语言 key，和页面按钮共用同一份来源。
     */
    public List<String> getEnabledLanguageKeys() {
        return getEnabledLanguageOptions().stream()
                .map(PracticeModeOption::getValue)
                .toList();
    }

    /**
     * 从缓存读取配置 JSON；缓存缺失或过期时按注册类型和 key 原子刷新。
     */
    private String loadConfigJson(String registryType, String itemKey) {
        long now = System.currentTimeMillis();
        String cacheKey = registryType + ":" + itemKey;
        CachedEntry<String> cached = configJsonCache.compute(cacheKey, (ignoredKey, current) -> {
            if (current != null && current.expiresAtMillis > now) {
                return current;
            }

            String value = configJsonLoader.apply(registryType, itemKey);
            return new CachedEntry<>(value, now + CACHE_TTL_MILLIS);
        });
        return cached.value;
    }

    /**
     * 从缓存读取练习模式；缓存缺失或过期时按注册类型原子刷新。
     */
    private List<PracticeModeOption> loadPracticeModes(String registryType) {
        return loadRegisteredOptions(registryType, TypeRegistryService::isSupportedPracticeMode, TypeRegistryService::safeLabel);
    }

    /**
     * 从缓存读取学习强度档位，并过滤掉后端未实现的档位 key。
     */
    private List<PracticeModeOption> loadReviewLoadProfiles(String registryType) {
        return loadRegisteredOptions(
                registryType,
                TypeRegistryService::isSupportedReviewLoadProfile,
                TypeRegistryService::safeReviewLoadProfileLabel
        );
    }

    /**
     * 从缓存读取 DeepSeek 模型，并过滤掉后端未支持的模型 key。
     */
    private List<PracticeModeOption> loadDeepSeekModels(String registryType) {
        return loadRegisteredOptions(
                registryType,
                TypeRegistryService::isSupportedDeepSeekModel,
                TypeRegistryService::safeDeepSeekModelLabel
        );
    }

    /**
     * 从缓存读取界面语言，并过滤掉前后端未实现的语言 key。
     */
    private List<PracticeModeOption> loadLanguageOptions(String registryType) {
        return loadRegisteredOptions(
                registryType,
                TypeRegistryService::isSupportedLanguage,
                TypeRegistryService::safeLanguageLabel
        );
    }

    /**
     * 从缓存读取页面选项，并统一执行后端支持过滤和显示名兜底。
     */
    private List<PracticeModeOption> loadRegisteredOptions(
            String registryType,
            Predicate<PracticeModeOption> supportedPredicate,
            Function<PracticeModeOption, String> labelResolver) {
        long now = System.currentTimeMillis();
        CachedEntry<List<PracticeModeOption>> cached = practiceModesCache.compute(registryType, (ignoredKey, current) -> {
            if (current != null && current.expiresAtMillis > now) {
                return current;
            }

            List<PracticeModeOption> value = practiceModesLoader.apply(registryType);
            List<PracticeModeOption> safeValue = value == null
                    ? List.of()
                    : value.stream()
                            .filter(supportedPredicate)
                            .map(mode -> new PracticeModeOption(mode.getValue(), labelResolver.apply(mode)))
                            .toList();
            return new CachedEntry<>(safeValue, now + CACHE_TTL_MILLIS);
        });
        return cached.value;
    }

    /**
     * 判断练习模式是否为页面和队列共同支持的模式。
     */
    private static boolean isSupportedPracticeMode(PracticeModeOption mode) {
        return mode != null
                && mode.getValue() != null
                && !mode.getValue().isBlank()
                && SUPPORTED_PRACTICE_MODE_KEYS.contains(mode.getValue());
    }

    /**
     * 判断学习强度是否为调度器已经实现的档位。
     */
    private static boolean isSupportedReviewLoadProfile(PracticeModeOption mode) {
        return mode != null
                && mode.getValue() != null
                && !mode.getValue().isBlank()
                && PracticeReviewLoadProfile.isSupported(mode.getValue());
    }

    /**
     * 判断 DeepSeek 模型是否为后端已经支持的模型。
     */
    private static boolean isSupportedDeepSeekModel(PracticeModeOption mode) {
        return mode != null
                && mode.getValue() != null
                && !mode.getValue().isBlank()
                && SUPPORTED_DEEPSEEK_MODELS.contains(mode.getValue());
    }

    /**
     * 判断界面语言是否为前端已经提供文案包的语言。
     */
    private static boolean isSupportedLanguage(PracticeModeOption mode) {
        return mode != null
                && mode.getValue() != null
                && !mode.getValue().isBlank()
                && SUPPORTED_LANGUAGE_KEYS.contains(mode.getValue());
    }

    /**
     * 复制练习模式列表，避免调用方修改内部默认值或缓存值。
     */
    private static List<PracticeModeOption> copyPracticeModes(List<PracticeModeOption> modes) {
        return modes.stream()
                .map(mode -> new PracticeModeOption(mode.getValue(), mode.getLabel()))
                .toList();
    }

    /**
     * 从映射 JSON 中读取 profile_name，缺失或格式错误时返回 null。
     */
    private String readProfileName(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return null;
        }

        try {
            JsonNode profileName = objectMapper.readTree(mappingJson).path("profile_name");
            return profileName.isMissingNode() || profileName.isNull() ? null : profileName.asString();
        } catch (Exception ex) {
            return null;
        }
    }

    /** label 非空时用 label，否则用 fallback 函数取兜底文案。 */
    private static String resolveLabel(PracticeModeOption mode, Function<String, String> fallback) {
        String label = mode.getLabel();
        return label != null && !label.isBlank() ? label : fallback.apply(mode.getValue());
    }

    private static String safeLabel(PracticeModeOption mode) {
        return resolveLabel(mode, TypeRegistryService::defaultPracticeModeLabel);
    }

    private static String safeReviewLoadProfileLabel(PracticeModeOption mode) {
        return resolveLabel(mode, key -> PracticeReviewLoadProfile.fromKey(key).label());
    }

    private static String safeDeepSeekModelLabel(PracticeModeOption mode) {
        return resolveLabel(mode, Function.identity());
    }

    private static String safeLanguageLabel(PracticeModeOption mode) {
        return resolveLabel(mode, TypeRegistryService::defaultLanguageLabel);
    }

    /**
     * 返回内置练习模式的页面文案。
     */
    private static String defaultPracticeModeLabel(String itemKey) {
        return switch (itemKey) {
            case "a2b" -> "Side A → Side B";
            case "b2a" -> "Side B → Side A";
            case "random" -> "Random";
            default -> itemKey;
        };
    }

    /**
     * 返回内置界面语言的页面文案；从 DEFAULT_LANGUAGE_OPTIONS 查找，未知 key 原样返回。
     */
    private static String defaultLanguageLabel(String itemKey) {
        return DEFAULT_LANGUAGE_OPTIONS.stream()
                .filter(o -> o.getValue().equals(itemKey))
                .map(PracticeModeOption::getLabel)
                .findFirst()
                .orElse(itemKey);
    }

    /**
     * 保存缓存值和过期时间。
     */
    private record CachedEntry<T>(T value, long expiresAtMillis) {
    }
}
