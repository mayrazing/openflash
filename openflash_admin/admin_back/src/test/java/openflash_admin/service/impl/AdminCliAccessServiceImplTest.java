package openflash_admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.dto.OfferingAccessMetadata;
import openflash_admin.dto.UserAccessPage;
import openflash_admin.entity.AdminUser;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.EnabledOfferingRow;
import openflash_admin.mapper.AdminPlatformAiMapper.UserAccessOverrideRow;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.service.AdminCliAccessService;

class AdminCliAccessServiceImplTest {

    @Test
    void batchReadResolvesOverrideBeforeDefaultWithOnlyTwoMapperQueries() {
        Fixture fixture = fixture();
        List<AdminUser> users = List.of(user(8L), user(9L));
        when(fixture.mapper.findEnabledOfferings()).thenReturn(List.of(
                codexOffering(false), apiOffering(true)));
        when(fixture.mapper.findUserAccessOverrides(List.of(8L, 9L))).thenReturn(List.of(
                new UserAccessOverrideRow(8L, "platform-codex-cli", true),
                new UserAccessOverrideRow(9L, "platform-api-gpt", false)));
        when(fixture.runtime.listClis()).thenReturn(List.of(new CliSnapshot(
                "codex", "platform-codex", "platform-codex-cli", "NOT_LOGGED_IN")));

        UserAccessPage page = fixture.service.accessForUsers(users);

        assertThat(page.runtimeAvailable()).isTrue();
        assertThat(page.clis()).containsExactly(new CliSnapshot(
                "codex", "platform-codex", "platform-codex-cli", "NOT_LOGGED_IN"));
        assertThat(page.offerings()).containsExactly(
                new OfferingAccessMetadata(
                        "platform-codex-cli", "PLATFORM", "platform-codex", "CLI",
                        "CODEX_APP_SERVER", "codex", null, false),
                new OfferingAccessMetadata(
                        "platform-api-gpt", "PLATFORM", "platform-api", "API",
                        "ANTHROPIC", null, "gpt-5.4", true));
        assertThat(page.accessByUserId().get(8L).offeringAccess()).isEqualTo(Map.of(
                "platform-codex-cli", true,
                "platform-api-gpt", true));
        assertThat(page.accessByUserId().get(8L).cliAccess())
                .isEqualTo(Map.of("codex", true));
        assertThat(page.accessByUserId().get(9L).offeringAccess()).isEqualTo(Map.of(
                "platform-codex-cli", false,
                "platform-api-gpt", false));
        assertThat(page.accessByUserId().get(9L).cliAccess())
                .isEqualTo(Map.of("codex", false));
        verify(fixture.mapper, times(1)).findEnabledOfferings();
        verify(fixture.mapper, times(1)).findUserAccessOverrides(List.of(8L, 9L));
        verify(fixture.runtime, times(1)).listClis();
    }

    @Test
    void runtimeOfflineKeepsDatabaseOfferingsClisAndEveryUserAccessRow() {
        Fixture fixture = fixture();
        List<AdminUser> users = List.of(user(8L), user(9L));
        when(fixture.mapper.findEnabledOfferings()).thenReturn(List.of(
                codexOffering(false), apiOffering(true)));
        when(fixture.mapper.findUserAccessOverrides(List.of(8L, 9L)))
                .thenReturn(List.of());
        when(fixture.runtime.listClis()).thenThrow(new AdminRuntimeUnavailableException());

        UserAccessPage page = fixture.service.accessForUsers(users);

        assertThat(page.runtimeAvailable()).isFalse();
        assertThat(page.clis()).containsExactly(new CliSnapshot(
                "codex", "platform-codex", "platform-codex-cli", "ERROR"));
        assertThat(page.offerings()).hasSize(2);
        assertThat(page.accessByUserId()).containsOnlyKeys(8L, 9L);
        assertThat(page.accessByUserId().get(8L).offeringAccess())
                .containsEntry("platform-api-gpt", true)
                .containsEntry("platform-codex-cli", false);
    }

    @Test
    void emptyUserResultNeverCallsOverrideQueryOrBuildsAnEmptyInClause() {
        Fixture fixture = fixture();
        when(fixture.mapper.findEnabledOfferings())
                .thenReturn(List.of(codexOffering(false)));
        when(fixture.runtime.listClis()).thenThrow(new AdminRuntimeUnavailableException());

        UserAccessPage page = fixture.service.accessForUsers(List.of());

        assertThat(page.accessByUserId()).isEmpty();
        assertThat(page.clis()).hasSize(1);
        verify(fixture.mapper).findEnabledOfferings();
        verify(fixture.mapper, never()).findUserAccessOverrides(any());
    }

    @Test
    void authAndDatabaseErrorsAreNotSwallowedAsRuntimeOffline() {
        Fixture fixture = fixture();
        when(fixture.mapper.findEnabledOfferings())
                .thenReturn(List.of(codexOffering(false)));
        when(fixture.mapper.findUserAccessOverrides(List.of(8L))).thenReturn(List.of());
        when(fixture.runtime.listClis())
                .thenThrow(new AdminException(AdminErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> fixture.service.accessForUsers(List.of(user(8L))))
                .isInstanceOf(AdminException.class)
                .satisfies(failure -> assertThat(((AdminException) failure).getErrorCode())
                        .isEqualTo(AdminErrorCode.FORBIDDEN));

        RuntimeException databaseFailure = new IllegalStateException("database failed");
        when(fixture.mapper.findEnabledOfferings()).thenThrow(databaseFailure);
        assertThatThrownBy(() -> fixture.service.accessForUsers(List.of(user(8L))))
                .isSameAs(databaseFailure);
    }

    @Test
    void accessWriteUsesRuntimeOfferingKeyWithoutRequiringCliLoginOrAvailableStatus() {
        Fixture fixture = fixture();
        when(fixture.users.findById(8L)).thenReturn(user(8L));
        when(fixture.runtime.listClis()).thenReturn(List.of(new CliSnapshot(
                "codex", "platform-codex", "platform-codex-cli", "NOT_LOGGED_IN")));
        when(fixture.mapper.findEnabledOfferingByKey("platform-codex-cli"))
                .thenReturn(codexOffering(false));

        fixture.service.updateAccess(8L, "codex", true);
        fixture.service.updateAccess(8L, "codex", false);

        verify(fixture.runtime).setUserAccess(
                "platform-codex-cli", 8L, new SetUserAccessRequest(true));
        verify(fixture.runtime).setUserAccess(
                "platform-codex-cli", 8L, new SetUserAccessRequest(false));
    }

    @Test
    void unknownCliDisabledOfferingAndMissingUserAreRejectedBeforeWrite() {
        Fixture fixture = fixture();
        when(fixture.users.findById(8L)).thenReturn(user(8L));
        when(fixture.runtime.listClis()).thenReturn(List.of(new CliSnapshot(
                "codex", "platform-codex", "platform-codex-cli", "ERROR")));

        assertInvalid(() -> fixture.service.updateAccess(8L, "unknown", true));
        assertInvalid(() -> fixture.service.updateAccess(8L, "codex", true));
        assertThatThrownBy(() -> fixture.service.updateAccess(99L, "codex", true))
                .isInstanceOf(AdminException.class)
                .satisfies(failure -> assertThat(((AdminException) failure).getErrorCode())
                        .isEqualTo(AdminErrorCode.USER_NOT_FOUND));
        verify(fixture.runtime, never()).setUserAccess(any(), anyLong(), any());
    }

    @Test
    void runtimeConnectivityFailureOnWriteRemains50301() {
        Fixture fixture = fixture();
        when(fixture.users.findById(8L)).thenReturn(user(8L));
        when(fixture.runtime.listClis()).thenThrow(new AdminRuntimeUnavailableException());

        assertThatThrownBy(() -> fixture.service.updateAccess(8L, "codex", true))
                .isInstanceOf(AdminRuntimeUnavailableException.class);

        verify(fixture.mapper, never()).findEnabledOfferingByKey(any());
    }

    private static Fixture fixture() {
        AdminUserMapper users = mock(AdminUserMapper.class);
        AdminPlatformAiMapper mapper = mock(AdminPlatformAiMapper.class);
        AiRuntimeAdminClient runtime = mock(AiRuntimeAdminClient.class);
        return new Fixture(
                users, mapper, runtime, new AdminCliAccessServiceImpl(users, mapper, runtime));
    }

    private static AdminUser user(long id) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setDeleted(0);
        return user;
    }

    private static EnabledOfferingRow codexOffering(boolean defaultAccess) {
        return new EnabledOfferingRow(
                21L, "platform-codex-cli", null, defaultAccess, 0,
                "platform-codex", "CLI", "CODEX_APP_SERVER", "codex");
    }

    private static EnabledOfferingRow apiOffering(boolean defaultAccess) {
        return new EnabledOfferingRow(
                22L, "platform-api-gpt", "gpt-5.4", defaultAccess, 1,
                "platform-api", "API", "ANTHROPIC", null);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AdminException.class)
                .satisfies(failure -> assertThat(((AdminException) failure).getErrorCode())
                        .isEqualTo(AdminErrorCode.INVALID_REQUEST));
    }

    private record Fixture(
            AdminUserMapper users,
            AdminPlatformAiMapper mapper,
            AiRuntimeAdminClient runtime,
            AdminCliAccessService service) {
    }
}
