package openflash_core.service.impl;

import java.util.List;
import java.util.Objects;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.User;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.PluginInstallMapper;
import openflash_core.mapper.PracticeSessionStoreMapper;
import openflash_core.mapper.UserFeatureFlagMapper;
import openflash_core.mapper.UserMapper;
import openflash_core.mapper.UserSettingsMapper;
import openflash_core.mapper.UserUploadMapper;
import openflash_core.spi.UserAccountInvalidatedEvent;
import openflash_core.spi.UserAccountInvalidatedEvent.Reason;
import openflash_core.spi.UserDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在单个事务内封禁、解封或永久删除用户账号。 */
@Service
public class InternalUserAdminServiceImpl {

    private final UserMapper userMapper;
    private final DeckMapper deckMapper;
    private final DeckDataDeletionServiceImpl deckDataDeletionService;
    private final CardMediaMapper cardMediaMapper;
    private final CardProgressMapper cardProgressMapper;
    private final PracticeSessionStoreMapper practiceSessionStoreMapper;
    private final PluginInstallMapper pluginInstallMapper;
    private final UserFeatureFlagMapper userFeatureFlagMapper;
    private final UserAiConfigMapper userAiConfigMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final UserUploadMapper userUploadMapper;
    private final UploadFileDeletionTaskProducer uploadFileDeletionTaskProducer;
    private final UserAiClientFactory aiClientFactory;
    private final ApplicationEventPublisher eventPublisher;

    public InternalUserAdminServiceImpl(
            UserMapper userMapper,
            DeckMapper deckMapper,
            DeckDataDeletionServiceImpl deckDataDeletionService,
            CardMediaMapper cardMediaMapper,
            CardProgressMapper cardProgressMapper,
            PracticeSessionStoreMapper practiceSessionStoreMapper,
            PluginInstallMapper pluginInstallMapper,
            UserFeatureFlagMapper userFeatureFlagMapper,
            UserAiConfigMapper userAiConfigMapper,
            UserSettingsMapper userSettingsMapper,
            UserUploadMapper userUploadMapper,
            UploadFileDeletionTaskProducer uploadFileDeletionTaskProducer,
            UserAiClientFactory aiClientFactory,
            ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.deckMapper = deckMapper;
        this.deckDataDeletionService = deckDataDeletionService;
        this.cardMediaMapper = cardMediaMapper;
        this.cardProgressMapper = cardProgressMapper;
        this.practiceSessionStoreMapper = practiceSessionStoreMapper;
        this.pluginInstallMapper = pluginInstallMapper;
        this.userFeatureFlagMapper = userFeatureFlagMapper;
        this.userAiConfigMapper = userAiConfigMapper;
        this.userSettingsMapper = userSettingsMapper;
        this.userUploadMapper = userUploadMapper;
        this.uploadFileDeletionTaskProducer = uploadFileDeletionTaskProducer;
        this.aiClientFactory = aiClientFactory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void setBanned(Long actorUserId, Long targetUserId, boolean banned) {
        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        requireActiveAdmin(actorUserId, activeAdminIds);
        User target = requireTarget(targetUserId);
        rejectSelf(actorUserId, targetUserId);
        if (banned && isAvailableAdmin(target)) {
            requireAnotherActiveAdmin(targetUserId, activeAdminIds);
        }
        if (Objects.equals(target.getBanned(), banned ? 1 : 0)) {
            return;
        }
        if (userMapper.updateBannedAndIncrementAuthVersion(targetUserId, banned) != 1) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        aiClientFactory.evict(targetUserId);
        if (banned) {
            eventPublisher.publishEvent(new UserAccountInvalidatedEvent(targetUserId, Reason.BANNED));
        }
    }

    @Transactional
    public void deleteUser(Long actorUserId, Long targetUserId) {
        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        requireActiveAdmin(actorUserId, activeAdminIds);
        User target = requireTarget(targetUserId);
        rejectSelf(actorUserId, targetUserId);
        if (isAvailableAdmin(target)) {
            requireAnotherActiveAdmin(targetUserId, activeAdminIds);
        }

        List<String> uploadPaths = Objects.requireNonNull(
                userUploadMapper.findPathsByUserId(targetUserId), "upload paths must not be null");
        for (String path : uploadPaths) {
            Long lockedOwnerId = userUploadMapper.lockOwnerIdByPath(path);
            if (!Objects.equals(lockedOwnerId, targetUserId)) {
                continue;
            }
            if (cardMediaMapper.lockFirstReferenceIdByOtherUser(path, targetUserId) == null) {
                uploadFileDeletionTaskProducer.enqueue(path);
            }
        }

        List<Long> deckIds = Objects.requireNonNull(
                deckMapper.findIdsByUserIdIncludingDeleted(targetUserId), "deck ids must not be null");
        for (Long deckId : deckIds) {
            deckDataDeletionService.deleteOwnedDeck(targetUserId, deckId);
        }

        cardProgressMapper.deleteByUserId(targetUserId);
        practiceSessionStoreMapper.deleteByUserId(targetUserId);
        pluginInstallMapper.deleteByUserId(targetUserId);
        userFeatureFlagMapper.deleteByUserId(targetUserId);
        userAiConfigMapper.deleteByUserId(targetUserId);
        userSettingsMapper.deleteByUserId(targetUserId);
        userUploadMapper.deleteByUserId(targetUserId);
        aiClientFactory.evict(targetUserId);
        eventPublisher.publishEvent(new UserDeletedEvent(targetUserId));
        if (userMapper.deleteById(targetUserId) != 1) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        eventPublisher.publishEvent(new UserAccountInvalidatedEvent(targetUserId, Reason.DELETED));
    }

    private void requireActiveAdmin(Long actorUserId, List<Long> activeAdminIds) {
        if (activeAdminIds == null || !activeAdminIds.contains(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private User requireTarget(Long targetUserId) {
        User target = userMapper.lockById(targetUserId);
        if (target == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return target;
    }

    private void rejectSelf(Long actorUserId, Long targetUserId) {
        if (Objects.equals(actorUserId, targetUserId)) {
            throw new AppException(ErrorCode.SELF_ACCOUNT_MUTATION);
        }
    }

    private boolean isAvailableAdmin(User user) {
        return "ADMIN".equals(user.getRole())
            && Boolean.TRUE.equals(user.getAdminApproved())
            && Integer.valueOf(0).equals(user.getBanned());
    }

    private void requireAnotherActiveAdmin(Long targetUserId, List<Long> activeAdminIds) {
        boolean targetWasLocked = activeAdminIds.contains(targetUserId);
        boolean anotherExists = activeAdminIds.stream()
                .anyMatch(id -> !Objects.equals(id, targetUserId));
        if (!targetWasLocked || !anotherExists) {
            throw new AppException(ErrorCode.LAST_ADMIN_REQUIRED);
        }
    }
}
