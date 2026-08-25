package openflash_core.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AiErrorCode;
import openflash_core.common.AiSource;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.service.UserAiConfigService;
import openflash_core.entity.UserAiConfig;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.entity.PlatformAiOffering;
import openflash_core.entity.UserActiveAiSelection;
import openflash_core.entity.UserPlatformAiPreference;
import openflash_core.mapper.PlatformAiOfferingMapper;
import openflash_core.mapper.PlatformAiUserAccessMapper;
import openflash_core.mapper.UserActiveAiSelectionMapper;
import openflash_core.mapper.UserPlatformAiPreferenceMapper;
import openflash_core.client.AiRuntimeCoreClient;
import openflash_core.common.AppException;

/** 用户 AI 列表、偏好与唯一激活项的权威入口. */
@Service
public class UnifiedAiSelectionServiceImpl {

    private final UserAiConfigMapper userConfigs;
    private final PlatformAiOfferingMapper offerings;
    private final UserPlatformAiPreferenceMapper preferences;
    private final UserActiveAiSelectionMapper activeSelections;
    private final AiRuntimeCoreClient runtimeClient;

    public UnifiedAiSelectionServiceImpl(
            UserAiConfigMapper userConfigs,
            PlatformAiOfferingMapper offerings,
            PlatformAiUserAccessMapper access,
            UserPlatformAiPreferenceMapper preferences,
            UserActiveAiSelectionMapper activeSelections,
            AiRuntimeCoreClient runtimeClient) {
        this.userConfigs = userConfigs;
        this.offerings = offerings;
        this.preferences = preferences;
        this.activeSelections = activeSelections;
        this.runtimeClient = runtimeClient;
    }

    public List<AiProviderView> listProviders(Long userId) {
        requireUserId(userId);
        UserActiveAiSelection active = activeSelections.findByUserId(userId);
        List<AiProviderView> result = new ArrayList<>();
        for (UserAiConfig config : userConfigs.findAllByUserId(userId)) {
            if (UserAiConfigService.CODEX_PROVIDER_KEY.equals(config.getProvider())) continue;
            result.add(AiProviderView.user(config, active));
        }

        Map<String, AiRuntimeCoreClient.OfferingSnapshot> runtimeOfferings = new HashMap<>();
        try {
            for (AiRuntimeCoreClient.OfferingSnapshot offering : runtimeClient.listOfferings(userId)) {
                if (offering.source() == AiSource.PLATFORM) {
                    runtimeOfferings.put(offering.offeringKey(), offering);
                }
            }
        } catch (RuntimeException ignored) {
            // Runtime 离线不隐藏 DB 注册项, 只投影 ERROR.
        }
        for (PlatformAiOffering offering : offerings.findVisibleByUserId(userId)) {
            UserPlatformAiPreference preference = preferences.find(userId, offering.id());
            AiRuntimeCoreClient.OfferingSnapshot runtime = runtimeOfferings.get(offering.offeringKey());
            result.add(AiProviderView.platform(offering, preference, active,
                    runtime == null ? "ERROR" : runtime.runtimeStatus()));
        }
        return List.copyOf(result);
    }

    public ActiveAiSelectionDto requireActive(Long userId) {
        requireUserId(userId);
        UserActiveAiSelection selected = activeSelections.findByUserId(userId);
        if (selected == null || selected.source() == null) throw notConfigured();
        return switch (selected.source()) {
            case USER -> requireActiveUser(userId, selected);
            case PLATFORM -> requireActivePlatform(userId, selected);
        };
    }

    public String resolveActiveModelOrNull(Long userId) {
        if (userId == null) return null;
        try {
            return requireActive(userId).model();
        } catch (AppException failure) {
            return null;
        }
    }

    public AiRuntimeCoreClient.ModelsSnapshot listPlatformModels(
            Long userId, String offeringKey) {
        requireUsableOffering(userId, offeringKey);
        try {
            return runtimeClient.listModels(userId, offeringKey);
        } catch (RuntimeException failure) {
            return new AiRuntimeCoreClient.ModelsSnapshot("ERROR", List.of());
        }
    }

    @Transactional
    public void activateUserProvider(Long userId, String providerKey) {
        requireUserId(userId);
        requireText(providerKey);
        if (UserAiConfigService.CODEX_PROVIDER_KEY.equals(providerKey)
                || userConfigs.findByUserIdAndProvider(userId, providerKey) == null) {
            throw notConfigured();
        }
        activeSelections.upsert(new UserActiveAiSelection(
                userId, AiSource.USER, providerKey, null));
    }

    @Transactional
    public void activatePlatformOffering(Long userId, String offeringKey) {
        PlatformAiOffering offering = requireUsableOffering(userId, offeringKey);
        if (offering.modelKey() == null) requireDynamicPreference(userId, offering.id());
        activeSelections.upsert(new UserActiveAiSelection(
                userId, AiSource.PLATFORM, null, offering.id()));
    }

    @Transactional
    public void savePlatformCliPreference(
            Long userId, String offeringKey, String model, String effort) {
        requireText(model);
        requireText(effort);
        PlatformAiOffering offering = requireUsableOffering(userId, offeringKey);
        if (offering.modelKey() != null || !"CLI".equals(offering.kind())) {
            throw invalidSelection();
        }
        AiRuntimeCoreClient.ModelsSnapshot catalog;
        try {
            catalog = runtimeClient.listModels(userId, offeringKey);
        } catch (RuntimeException failure) {
            throw runtimeUnavailable();
        }
        boolean valid = catalog.models().stream().anyMatch(candidate ->
                model.equals(candidate.model())
                        && candidate.supportedReasoningEfforts().stream()
                        .anyMatch(candidateEffort -> effort.equals(
                                candidateEffort.reasoningEffort())));
        if (!valid) throw invalidSelection();
        preferences.upsert(new UserPlatformAiPreference(userId, offering.id(), model, effort));
    }

    public boolean isActiveSelectionUsable(Long userId) {
        try {
            requireActive(userId);
            return true;
        } catch (AppException failure) {
            return false;
        }
    }

    private ActiveAiSelectionDto requireActiveUser(
            Long userId, UserActiveAiSelection selected) {
        if (selected.userProviderKey() == null) throw notConfigured();
        UserAiConfig config = userConfigs.findByUserIdAndProvider(
                userId, selected.userProviderKey());
        if (config == null) throw notConfigured();
        return new ActiveAiSelectionDto(
                AiSource.USER, config.getProvider(), null,
                config.getConfigValue("protocol"), config.getConfigValue("model"),
                config.getConfigValue("reasoningEffort"),
                providerInstanceIdentity(config));
    }

    private ActiveAiSelectionDto requireActivePlatform(
            Long userId, UserActiveAiSelection selected) {
        if (selected.offeringId() == null) throw notConfigured();
        PlatformAiOffering offering = offerings.findByIdAndUserId(selected.offeringId(), userId);
        if (offering == null || !offering.usable()) throw notConfigured();
        UserPlatformAiPreference preference = offering.modelKey() == null
                ? requireDynamicPreference(userId, offering.id()) : null;
        return new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, offering.offeringKey(), offering.protocol(),
                offering.modelKey() == null ? preference.model() : offering.modelKey(),
                offering.modelKey() == null ? preference.reasoningEffort() : null);
    }

    /** 用户供应商的地址或凭据变化时必须切断旧缓存身份, 但不把凭据本身写入缓存键. */
    private String providerInstanceIdentity(UserAiConfig config) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = lengthPrefixed(config.getProvider())
                    + lengthPrefixed(config.getConfigValue("protocol"))
                    + lengthPrefixed(config.getConfigValue("baseUrl"))
                    + lengthPrefixed(config.getConfigValue("apiKeyEnc"));
            return java.util.HexFormat.of().formatHex(
                    digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String lengthPrefixed(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }

    private PlatformAiOffering requireUsableOffering(Long userId, String offeringKey) {
        requireUserId(userId);
        requireText(offeringKey);
        PlatformAiOffering offering = offerings.findByKeyAndUserId(offeringKey, userId);
        if (offering == null || !offering.usable()) throw notConfigured();
        return offering;
    }

    private UserPlatformAiPreference requireDynamicPreference(Long userId, long offeringId) {
        UserPlatformAiPreference preference = preferences.find(userId, offeringId);
        if (preference == null || isBlank(preference.model())
                || isBlank(preference.reasoningEffort())) throw invalidSelection();
        return preference;
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw notConfigured();
    }

    private static void requireText(String value) {
        if (isBlank(value)) throw invalidSelection();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AppException notConfigured() {
        return new AppException(AiErrorCode.AI_NOT_CONFIGURED);
    }

    private static AppException invalidSelection() {
        return new AppException(AiErrorCode.AI_CODEX_SELECTION_INVALID);
    }

    private static AppException runtimeUnavailable() {
        return new AppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
    }

    public record AiProviderView(
            String id,
            String providerKey,
            AiSource source,
            String offeringKey,
            String kind,
            String protocol,
            String displayNameKey,
            String displayName,
            String website,
            String note,
            String baseUrl,
            String model,
            String reasoningEffort,
            boolean apiKeyConfigured,
            boolean active,
            boolean template,
            boolean builtIn,
            boolean editable,
            boolean deletable,
            boolean accessGranted,
            String runtimeStatus) {

        private static AiProviderView user(
                UserAiConfig config, UserActiveAiSelection active) {
            return new AiProviderView(
                    "USER:" + config.getProvider(), config.getProvider(),
                    AiSource.USER, null, "API_KEY",
                    config.getConfigValue("protocol"), null,
                    config.getConfigValue("displayName"), config.getConfigValue("website"),
                    config.getConfigValue("note"), config.getConfigValue("baseUrl"),
                    config.getConfigValue("model"), config.getConfigValue("reasoningEffort"),
                    !isBlank(config.getConfigValue("apiKeyEnc")),
                    active != null && active.source() == AiSource.USER
                            && config.getProvider().equals(active.userProviderKey()),
                    false, false, true, true, true, null);
        }

        private static AiProviderView platform(
                PlatformAiOffering offering, UserPlatformAiPreference preference,
                UserActiveAiSelection active, String runtimeStatus) {
            return new AiProviderView(
                    "PLATFORM:" + offering.offeringKey(), offering.offeringKey(),
                    AiSource.PLATFORM, offering.offeringKey(),
                    offering.kind(), offering.protocol(),
                    "settings.platformAi." + offering.offeringKey() + ".name",
                    null, null, null, null,
                    offering.modelKey() == null && preference != null
                            ? preference.model() : offering.modelKey(),
                    preference == null ? null : preference.reasoningEffort(), false,
                    active != null && active.source() == AiSource.PLATFORM
                            && Long.valueOf(offering.id()).equals(active.offeringId()),
                    false, true, "CLI".equals(offering.kind()), false,
                    offering.accessGranted(), runtimeStatus);
        }
    }
}
