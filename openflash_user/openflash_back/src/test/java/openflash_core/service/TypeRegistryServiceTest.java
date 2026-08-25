package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import openflash_core.config.AiProperties;
import openflash_core.entity.PracticeModeOption;

class TypeRegistryServiceTest {

    /**
     * 验证 AI 功能映射缺失时，页面不会拿到模型配置。
     */
    @Test
    void resolveAiProfileReturnsNullWhenMappingDoesNotExist() {
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, registryType -> List.of());

        assertNull(service.resolveAiProfile("card-ai-markdown"));
    }

    /**
     * 验证 AI 功能映射存在但 profile 配置缺失时，页面不会拿到模型配置。
     */
    @Test
    void resolveAiProfileReturnsNullWhenProfileDoesNotExist() {
        BiFunction<String, String, String> configLoader = (registryType, itemKey) -> {
            if ("ai_feature_mapping".equals(registryType) && "card-ai-markdown".equals(itemKey)) {
                return "{\"profile_name\":\"ai_cache\"}";
            }
            return null;
        };
        TypeRegistryService service = new TypeRegistryService(configLoader, registryType -> List.of());

        assertNull(service.resolveAiProfile("card-ai-markdown"));
    }

    /**
     * 验证 AI 功能映射和 profile 都存在时，字段按数据库 JSON 返回。
     */
    @Test
    void resolveAiProfileReturnsProfileFieldsWhenMappingAndProfileExist() {
        BiFunction<String, String, String> configLoader = (registryType, itemKey) -> {
            if ("ai_feature_mapping".equals(registryType) && "card-ai-markdown".equals(itemKey)) {
                return "{\"profile_name\":\"ai_cache\"}";
            }
            if ("ai_profile".equals(registryType) && "ai_cache".equals(itemKey)) {
                return "{\"model\":\"qwen3:4b\",\"temperature\":0.2,\"system\":\"test prompt\"}";
            }
            return null;
        };
        TypeRegistryService service = new TypeRegistryService(configLoader, registryType -> List.of());

        AiProperties.AiProfile profile = service.resolveAiProfile("card-ai-markdown");

        assertEquals("ai_cache", profile.getName());
        assertEquals("qwen3:4b", profile.getModel());
        assertEquals(0.2, profile.getTemperature());
        assertEquals("test prompt", profile.getSystem());
    }

    /**
     * 验证启用练习模式列表按数据库顺序原样返回。
     */
    @Test
    void getEnabledPracticeModeKeysReturnsDatabaseListInOrder() {
        Function<String, List<String>> itemKeysLoader = registryType -> List.of("a2b", "b2a", "random");
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, itemKeysLoader);

        assertEquals(List.of("a2b", "b2a", "random"), service.getEnabledPracticeModeKeys());
    }

    /**
     * 验证数据库无启用练习模式时，key 列表也回退固定三模式。
     */
    @Test
    void getEnabledPracticeModeKeysFallsBackToDefaultModesWhenDatabaseReturnsNull() {
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, registryType -> null);

        assertEquals(List.of("a2b", "b2a", "random"), service.getEnabledPracticeModeKeys());
    }

    /**
     * 验证启用练习模式会带显示文案，并按数据库顺序返回。
     */
    @Test
    void getEnabledPracticeModesReturnsOptionsInDatabaseOrder() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> List.of(
                new PracticeModeOption("a2b", "A面→B面"),
                new PracticeModeOption("random", "随机双向")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> modes = service.getEnabledPracticeModes();

        assertEquals(2, modes.size());
        assertEquals("a2b", modes.get(0).getValue());
        assertEquals("A面→B面", modes.get(0).getLabel());
        assertEquals("random", modes.get(1).getValue());
        assertEquals("随机双向", modes.get(1).getLabel());
    }

    /**
     * 验证数据库没有启用模式时，页面仍显示固定三种练习模式。
     */
    @Test
    void getEnabledPracticeModesFallsBackToDefaultModesWhenDatabaseReturnsEmpty() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> modes = service.getEnabledPracticeModes();

        assertEquals(List.of("a2b", "b2a", "random"), modes.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("Side A → Side B", "Side B → Side A", "Random"), modes.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证队列校验使用和页面按钮同一份模式来源。
     */
    @Test
    void getEnabledPracticeModeKeysUsesPracticeModeOptions() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> List.of(
                new PracticeModeOption("b2a", "B面→A面")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        assertEquals(List.of("b2a"), service.getEnabledPracticeModeKeys());
    }

    /**
     * 验证数据库返回未知、空值和空对象时，页面只显示后端支持的练习模式。
     */
    @Test
    void getEnabledPracticeModesFiltersUnsupportedAndBlankModes() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> Arrays.asList(
                new PracticeModeOption("foo", "未知"),
                new PracticeModeOption(" ", "空白"),
                null,
                new PracticeModeOption("a2b", "A面→B面")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> modes = service.getEnabledPracticeModes();

        assertEquals(1, modes.size());
        assertEquals("a2b", modes.get(0).getValue());
        assertEquals("A面→B面", modes.get(0).getLabel());
    }

    /**
     * 验证学习强度只返回后端已经实现的档位，并保留数据库显示文案。
     */
    @Test
    void getEnabledReviewLoadProfilesFiltersUnsupportedProfiles() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> Arrays.asList(
                new PracticeModeOption("relaxed", "轻松模式"),
                new PracticeModeOption("unknown", "未知"),
                new PracticeModeOption("intensive", "强化模式")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> profiles = service.getEnabledReviewLoadProfiles();

        assertEquals(List.of("relaxed", "intensive"), profiles.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("轻松模式", "强化模式"), profiles.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证数据库没有启用学习强度时，设置页仍显示内置三档。
     */
    @Test
    void getEnabledReviewLoadProfilesFallsBackToDefaultProfiles() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> profiles = service.getEnabledReviewLoadProfiles();

        assertEquals(List.of("relaxed", "standard", "intensive"), profiles.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("轻松", "标准", "强化"), profiles.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证 DeepSeek 模型来自类型注册表，并过滤掉后端未支持的模型。
     */
    @Test
    void getEnabledDeepSeekModelsFiltersUnsupportedModels() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> Arrays.asList(
                new PracticeModeOption("deepseek-v4-flash", "Flash"),
                new PracticeModeOption("deepseek-future", "未来模型"),
                new PracticeModeOption("deepseek-v4-pro", "")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> models = service.getEnabledDeepSeekModels();

        assertEquals(List.of("deepseek-v4-flash", "deepseek-v4-pro"), models.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("Flash", "deepseek-v4-pro"), models.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证数据库没有配置 DeepSeek 模型时，设置页仍显示内置模型。
     */
    @Test
    void getEnabledDeepSeekModelsFallsBackToDefaultModels() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> models = service.getEnabledDeepSeekModels();

        assertEquals(List.of("deepseek-v4-flash", "deepseek-v4-pro"), models.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("DeepSeek V4 Flash", "DeepSeek V4 Pro"), models.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证 AI provider kind 只返回数据库已启用且后端已实现的条目.
     */
    @Test
    void enabledAiProviderKindsOnlyReturnsImplementedDatabaseRows() {
        Function<String, List<PracticeModeOption>> optionsLoader = registryType ->
                "ai_provider_kind".equals(registryType)
                        ? List.of(
                                new PracticeModeOption("codex-cli", "codex-cli"),
                                new PracticeModeOption("unknown-kind", "unknown-kind"))
                        : List.of();
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                optionsLoader);

        assertEquals(List.of("codex-cli"), service.getEnabledAiProviderKinds());
    }

    /**
     * 验证数据库没有 AI provider kind 时保持旧 API-only 行为.
     */
    @Test
    void missingAiProviderRegistryKeepsOldApiOnlyBehavior() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of());

        assertEquals(List.of(), service.getEnabledAiProviderKinds());
    }

    /**
     * 验证界面语言来自类型注册表，并过滤掉前端没有文案包的语言。
     */
    @Test
    void getEnabledLanguageOptionsFiltersUnsupportedLanguages() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> Arrays.asList(
                new PracticeModeOption("en", "English"),
                new PracticeModeOption("ja", "日本語"),
                new PracticeModeOption("fi", "")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> languages = service.getEnabledLanguageOptions();

        assertEquals(List.of("en", "fi"), languages.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("English", "Suomi"), languages.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证数据库没有配置界面语言时，设置页仍显示内置四种语言。
     */
    @Test
    void getEnabledLanguageOptionsFallsBackToDefaultLanguages() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> languages = service.getEnabledLanguageOptions();

        assertEquals(List.of("zh", "en", "fi", "de"), languages.stream().map(PracticeModeOption::getValue).toList());
        assertEquals(List.of("中文", "English", "Suomi", "Deutsch"), languages.stream().map(PracticeModeOption::getLabel).toList());
    }

    /**
     * 验证设置保存校验和页面按钮使用同一份语言来源。
     */
    @Test
    void getEnabledLanguageKeysUsesLanguageOptions() {
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> List.of(
                new PracticeModeOption("de", "Deutsch")
        );
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        assertEquals(List.of("de"), service.getEnabledLanguageKeys());
    }

    /**
     * 验证调用方修改 DeepSeek 默认回退结果后，不会污染下一次设置页读取。
     */
    @Test
    void getEnabledDeepSeekModelsReturnsCopyOfDefaultModels() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> firstRead = service.getEnabledDeepSeekModels();
        firstRead.get(0).setLabel("被页面改坏");

        List<PracticeModeOption> secondRead = service.getEnabledDeepSeekModels();

        assertEquals("DeepSeek V4 Flash", secondRead.get(0).getLabel());
    }

    /**
     * 验证调用方修改默认回退结果后，不会污染下一次页面读取。
     */
    @Test
    void getEnabledPracticeModesReturnsCopyOfDefaultModes() {
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                registryType -> List.of()
        );

        List<PracticeModeOption> firstRead = service.getEnabledPracticeModes();
        firstRead.get(0).setValue("changed");
        firstRead.get(0).setLabel("已修改");
        List<PracticeModeOption> secondRead = service.getEnabledPracticeModes();

        assertEquals("a2b", secondRead.get(0).getValue());
        assertEquals("Side A → Side B", secondRead.get(0).getLabel());
    }

    /**
     * 验证调用方修改缓存结果后，不会污染下一次页面读取，且缓存期内仍只加载一次。
     */
    @Test
    void getEnabledPracticeModesReturnsCopyOfCachedModes() {
        AtomicInteger loadCount = new AtomicInteger();
        Function<String, List<PracticeModeOption>> modeOptionsLoader = registryType -> {
            loadCount.incrementAndGet();
            return List.of(new PracticeModeOption("a2b", "数据库文案"));
        };
        TypeRegistryService service = new TypeRegistryService(
                (registryType, itemKey) -> null,
                registryType -> List.of(),
                modeOptionsLoader
        );

        List<PracticeModeOption> firstRead = service.getEnabledPracticeModes();
        firstRead.get(0).setValue("changed");
        firstRead.get(0).setLabel("已修改");
        List<PracticeModeOption> secondRead = service.getEnabledPracticeModes();

        assertEquals("a2b", secondRead.get(0).getValue());
        assertEquals("数据库文案", secondRead.get(0).getLabel());
        assertEquals(1, loadCount.get());
    }

    /**
     * 验证 60 秒缓存期内，同一个 AI 映射和 profile 只各加载一次。
     */
    @Test
    void resolveAiProfileReusesMappingAndProfileCacheWithinCacheTtl() {
        AtomicInteger mappingLoadCount = new AtomicInteger();
        AtomicInteger profileLoadCount = new AtomicInteger();
        BiFunction<String, String, String> configLoader = (registryType, itemKey) -> {
            if ("ai_feature_mapping".equals(registryType) && "card-ai-markdown".equals(itemKey)) {
                mappingLoadCount.incrementAndGet();
                return "{\"profile_name\":\"ai_cache\"}";
            }
            if ("ai_profile".equals(registryType) && "ai_cache".equals(itemKey)) {
                profileLoadCount.incrementAndGet();
                return "{\"model\":\"qwen3:4b\",\"temperature\":0.2,\"system\":\"test prompt\"}";
            }
            return null;
        };
        TypeRegistryService service = new TypeRegistryService(configLoader, registryType -> List.of());

        assertEquals("qwen3:4b", service.resolveAiProfile("card-ai-markdown").getModel());
        assertEquals("qwen3:4b", service.resolveAiProfile("card-ai-markdown").getModel());

        assertEquals(1, mappingLoadCount.get());
        assertEquals(1, profileLoadCount.get());
    }

    /**
     * 验证页面取 AI 配置时，数据库 profile 存在就直接使用数据库结果。
     */
    @Test
    void aiPropertiesResolveProfilePrefersDatabaseProfile() {
        AiProperties.AiProfile dbProfile = new AiProperties.AiProfile();
        dbProfile.setModel("db-model");
        dbProfile.setTemperature(0.3);
        dbProfile.setSystem("db prompt");
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, registryType -> List.of()) {
            /**
             * 返回固定数据库 profile，模拟数据库配置命中。
             */
            @Override
            public AiProperties.AiProfile resolveAiProfile(String featureKey) {
                return dbProfile;
            }
        };
        AiProperties properties = new AiProperties();
        properties.setTypeRegistryService(service);

        AiProperties.AiProfile profile = properties.resolveProfile("card-ai-markdown");

        assertEquals("db-model", profile.getModel());
        assertEquals(0.3, profile.getTemperature());
        assertEquals("db prompt", profile.getSystem());
    }

    /**
     * 验证数据库没有 profile 时，页面仍使用 YAML profile。
     */
    @Test
    void aiPropertiesResolveProfileFallsBackToYamlProfileWhenDatabaseMissing() {
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, registryType -> List.of());
        AiProperties.AiProfile yamlProfile = new AiProperties.AiProfile();
        yamlProfile.setName("yaml-cache");
        yamlProfile.setModel("yaml-model");
        yamlProfile.setTemperature(0.4);
        yamlProfile.setSystem("yaml prompt");
        AiProperties properties = new AiProperties();
        properties.setTypeRegistryService(service);
        properties.setFeatureProfiles(Map.of("card-ai-markdown", "yaml-cache"));
        properties.setProfiles(List.of(yamlProfile));

        AiProperties.AiProfile profile = properties.resolveProfile("card-ai-markdown");

        assertEquals("yaml-model", profile.getModel());
        assertEquals(0.4, profile.getTemperature());
        assertEquals("yaml prompt", profile.getSystem());
    }

    /**
     * 验证 60 秒缓存期内，练习模式列表只加载一次。
     */
    @Test
    void getEnabledPracticeModeKeysReusesCacheWithinCacheTtl() {
        AtomicInteger loadCount = new AtomicInteger();
        Function<String, List<String>> itemKeysLoader = registryType -> {
            loadCount.incrementAndGet();
            return List.of("a2b", "b2a", "random");
        };
        TypeRegistryService service = new TypeRegistryService((registryType, itemKey) -> null, itemKeysLoader);

        assertEquals(List.of("a2b", "b2a", "random"), service.getEnabledPracticeModeKeys());
        assertEquals(List.of("a2b", "b2a", "random"), service.getEnabledPracticeModeKeys());

        assertEquals(1, loadCount.get());
    }
}
