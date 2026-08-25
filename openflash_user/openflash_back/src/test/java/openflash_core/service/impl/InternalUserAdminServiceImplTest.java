package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

class InternalUserAdminServiceImplTest {

    private UserMapper userMapper;
    private DeckMapper deckMapper;
    private DeckDataDeletionServiceImpl deckDeletionService;
    private CardProgressMapper cardProgressMapper;
    private CardMediaMapper cardMediaMapper;
    private PracticeSessionStoreMapper practiceSessionStoreMapper;
    private PluginInstallMapper pluginInstallMapper;
    private UserFeatureFlagMapper userFeatureFlagMapper;
    private UserAiConfigMapper userAiConfigMapper;
    private UserSettingsMapper userSettingsMapper;
    private UserUploadMapper userUploadMapper;
    private UploadFileDeletionTaskProducer uploadFileDeletionTaskProducer;
    private UserAiClientFactory aiClientFactory;
    private ApplicationEventPublisher eventPublisher;
    private InternalUserAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        deckMapper = mock(DeckMapper.class);
        deckDeletionService = mock(DeckDataDeletionServiceImpl.class);
        cardProgressMapper = mock(CardProgressMapper.class);
        cardMediaMapper = mock(CardMediaMapper.class);
        practiceSessionStoreMapper = mock(PracticeSessionStoreMapper.class);
        pluginInstallMapper = mock(PluginInstallMapper.class);
        userFeatureFlagMapper = mock(UserFeatureFlagMapper.class);
        userAiConfigMapper = mock(UserAiConfigMapper.class);
        userSettingsMapper = mock(UserSettingsMapper.class);
        userUploadMapper = mock(UserUploadMapper.class);
        uploadFileDeletionTaskProducer = mock(UploadFileDeletionTaskProducer.class);
        aiClientFactory = mock(UserAiClientFactory.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new InternalUserAdminServiceImpl(
                userMapper,
                deckMapper,
                deckDeletionService,
                cardMediaMapper,
                cardProgressMapper,
                practiceSessionStoreMapper,
                pluginInstallMapper,
                userFeatureFlagMapper,
                userAiConfigMapper,
                userSettingsMapper,
                userUploadMapper,
                uploadFileDeletionTaskProducer,
                aiClientFactory,
                eventPublisher);
    }

    @Test
    void cannotBanSelf() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(7L)).thenReturn(user(7L, "ADMIN", false));

        assertError(ErrorCode.SELF_ACCOUNT_MUTATION,
                () -> service.setBanned(7L, 7L, true));

        verify(userMapper, never()).updateBannedAndIncrementAuthVersion(7L, true);
    }

    @Test
    void actorMustBelongToLockedAvailableAdminSet() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(8L));

        assertError(ErrorCode.FORBIDDEN,
                () -> service.setBanned(7L, 8L, true));

        verify(userMapper, never()).lockById(8L);
    }

    @Test
    void cannotBanAdminMissingFromLockedAvailableAdminSnapshot() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "ADMIN", false));

        assertError(ErrorCode.LAST_ADMIN_REQUIRED,
                () -> service.setBanned(7L, 8L, true));

        verify(userMapper, never()).updateBannedAndIncrementAuthVersion(8L, true);
    }

    @Test
    void cannotDeleteAdminMissingFromLockedAvailableAdminSnapshot() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "ADMIN", false));

        assertError(ErrorCode.LAST_ADMIN_REQUIRED,
                () -> service.deleteUser(7L, 8L));

        verifyNoInteractions(deckMapper);
    }

    @Test
    void mutationLocksAvailableAdminsBeforeTargetWithoutSecondAdminQuery() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(userMapper.updateBannedAndIncrementAuthVersion(8L, true)).thenReturn(1);

        service.setBanned(7L, 8L, true);

        InOrder order = inOrder(userMapper);
        order.verify(userMapper).lockActiveAdminIds();
        order.verify(userMapper).lockById(8L);
        order.verify(userMapper).updateBannedAndIncrementAuthVersion(8L, true);
        verify(userMapper).lockActiveAdminIds();
        verify(eventPublisher).publishEvent(new UserAccountInvalidatedEvent(8L, Reason.BANNED));
    }

    @Test
    void missingTargetFailsAfterActorAndAdminSetAreLocked() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));

        assertError(ErrorCode.USER_NOT_FOUND,
                () -> service.setBanned(7L, 8L, true));
    }

    @Test
    void unbanIsIdempotentAfterActorAndAdminSetAreLocked() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", true));
        when(userMapper.updateBannedAndIncrementAuthVersion(8L, false)).thenReturn(1);

        service.setBanned(7L, 8L, false);

        verify(userMapper).updateBannedAndIncrementAuthVersion(8L, false);
        verify(userMapper).lockActiveAdminIds();
        verify(aiClientFactory).evict(8L);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unchangedBanStatusDoesNotWriteOrEvict() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", true));

        service.setBanned(7L, 8L, true);

        verify(userMapper, never()).updateBannedAndIncrementAuthVersion(8L, true);
        verifyNoInteractions(aiClientFactory);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void banUpdateCountMustBeExactlyOne() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(userMapper.updateBannedAndIncrementAuthVersion(8L, true)).thenReturn(2);

        assertError(ErrorCode.USER_NOT_FOUND,
                () -> service.setBanned(7L, 8L, true));

        verifyNoInteractions(aiClientFactory);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deletePurgesEveryOwnedLayerBeforeUserRow() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(deckMapper.findIdsByUserIdIncludingDeleted(8L)).thenReturn(List.of(11L, 12L));
        when(userUploadMapper.findPathsByUserId(8L)).thenReturn(List.of("/uploads/a.jpg"));
        when(userUploadMapper.lockOwnerIdByPath("/uploads/a.jpg")).thenReturn(8L);
        when(cardMediaMapper.lockFirstReferenceIdByOtherUser("/uploads/a.jpg", 8L)).thenReturn(null);
        when(userMapper.deleteById(8L)).thenReturn(1);

        service.deleteUser(7L, 8L);

        InOrder order = inOrder(userMapper, deckMapper, deckDeletionService, cardMediaMapper,
                userUploadMapper, uploadFileDeletionTaskProducer, cardProgressMapper,
                practiceSessionStoreMapper, pluginInstallMapper, userFeatureFlagMapper,
                userAiConfigMapper, userSettingsMapper, aiClientFactory, eventPublisher);
        order.verify(userMapper).lockActiveAdminIds();
        order.verify(userMapper).lockById(8L);
        order.verify(userUploadMapper).findPathsByUserId(8L);
        order.verify(userUploadMapper).lockOwnerIdByPath("/uploads/a.jpg");
        order.verify(cardMediaMapper).lockFirstReferenceIdByOtherUser("/uploads/a.jpg", 8L);
        order.verify(uploadFileDeletionTaskProducer).enqueue("/uploads/a.jpg");
        order.verify(deckMapper).findIdsByUserIdIncludingDeleted(8L);
        order.verify(deckDeletionService).deleteOwnedDeck(8L, 11L);
        order.verify(deckDeletionService).deleteOwnedDeck(8L, 12L);
        order.verify(cardProgressMapper).deleteByUserId(8L);
        order.verify(practiceSessionStoreMapper).deleteByUserId(8L);
        order.verify(pluginInstallMapper).deleteByUserId(8L);
        order.verify(userFeatureFlagMapper).deleteByUserId(8L);
        order.verify(userAiConfigMapper).deleteByUserId(8L);
        order.verify(userSettingsMapper).deleteByUserId(8L);
        order.verify(userUploadMapper).deleteByUserId(8L);
        order.verify(aiClientFactory).evict(8L);
        order.verify(eventPublisher).publishEvent(new UserDeletedEvent(8L));
        order.verify(userMapper).deleteById(8L);
        order.verify(eventPublisher).publishEvent(new UserAccountInvalidatedEvent(8L, Reason.DELETED));
        order.verifyNoMoreInteractions();
    }

    @Test
    void deleteKeepsSharedPhysicalUploadButStillDeletesOwnerRow() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(userUploadMapper.findPathsByUserId(8L)).thenReturn(List.of("/uploads/shared.jpg"));
        when(userUploadMapper.lockOwnerIdByPath("/uploads/shared.jpg")).thenReturn(8L);
        when(cardMediaMapper.lockFirstReferenceIdByOtherUser("/uploads/shared.jpg", 8L)).thenReturn(44L);
        when(deckMapper.findIdsByUserIdIncludingDeleted(8L)).thenReturn(List.of());
        when(userMapper.deleteById(8L)).thenReturn(1);

        service.deleteUser(7L, 8L);

        verify(uploadFileDeletionTaskProducer, never()).enqueue("/uploads/shared.jpg");
        verify(userUploadMapper).deleteByUserId(8L);
        verify(userMapper).deleteById(8L);
    }

    @Test
    void deleteSkipsPathWhoseOwnerChangedBeforeExactLock() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(userUploadMapper.findPathsByUserId(8L)).thenReturn(List.of("/uploads/moved.jpg"));
        when(userUploadMapper.lockOwnerIdByPath("/uploads/moved.jpg")).thenReturn(9L);
        when(deckMapper.findIdsByUserIdIncludingDeleted(8L)).thenReturn(List.of());
        when(userMapper.deleteById(8L)).thenReturn(1);

        service.deleteUser(7L, 8L);

        verify(cardMediaMapper, never()).lockFirstReferenceIdByOtherUser("/uploads/moved.jpg", 8L);
        verify(uploadFileDeletionTaskProducer, never()).enqueue("/uploads/moved.jpg");
        verify(userUploadMapper).deleteByUserId(8L);
    }

    @Test
    void finalUserDeleteCountMustBeExactlyOne() {
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.lockById(8L)).thenReturn(user(8L, "USER", false));
        when(deckMapper.findIdsByUserIdIncludingDeleted(8L)).thenReturn(List.of());
        when(userUploadMapper.findPathsByUserId(8L)).thenReturn(List.of());
        when(userMapper.deleteById(8L)).thenReturn(0);

        assertError(ErrorCode.USER_NOT_FOUND,
                () -> service.deleteUser(7L, 8L));

        verify(eventPublisher, never())
                .publishEvent(new UserAccountInvalidatedEvent(8L, Reason.DELETED));
    }

    @Test
    void mutationMethodsAreTransactional() throws Exception {
        Method setBanned = InternalUserAdminServiceImpl.class.getMethod(
                "setBanned", Long.class, Long.class, boolean.class);
        Method deleteUser = InternalUserAdminServiceImpl.class.getMethod(
                "deleteUser", Long.class, Long.class);

        assertNotNull(setBanned.getAnnotation(Transactional.class));
        assertNotNull(deleteUser.getAnnotation(Transactional.class));
    }

    private static User user(Long id, String role, boolean banned) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setAdminApproved("ADMIN".equals(role));
        user.setBanned(banned ? 1 : 0);
        user.setDeleted(0);
        return user;
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        AppException error = assertThrows(AppException.class, action::run);
        assertEquals(expected, error.getErrorCode());
    }
}
