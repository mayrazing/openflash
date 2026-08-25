package openflash_admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.common.AdminExceptionHandler;
import openflash_admin.client.OpenFlashCoreAdminClient;
import openflash_admin.entity.AdminUser;
import openflash_admin.interceptor.AdminAuthInterceptor;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.UserAccessOverrideRow;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.security.PasswordVerifier;
import openflash_admin.security.AdminLoginAttemptGuard;
import openflash_admin.service.AdminSessionService;
import openflash_admin.service.impl.AdminCliAccessServiceImpl;
import openflash_admin.service.impl.AdminSessionServiceImpl;
import openflash_admin.service.impl.AdminUserAccountServiceImpl;
import openflash_admin.service.impl.AdminUserServiceImpl;

class AdminUserControllerTest {

    @Test
    void adminListsSafeUserRowsAndSearchesUsernameOrNickname() throws Exception {
        Fixture fixture = fixture("ADMIN");
        AdminUser listedUser = user(8L, "amy", "Amy Alias", "USER", 0, true);
        listedUser.setBanned(1);
        fixture.userMapper.add(listedUser);
        fixture.platformMapper.overrides.add(
                new UserAccessOverrideRow(8L, "platform-codex-cli", true));

        fixture.mvc.perform(get("/api/admin/users")
                .queryParam("query", "Amy")
                .session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeAvailable").value(true))
                .andExpect(jsonPath("$.data.clis[0].cliKey").value("codex"))
                .andExpect(jsonPath("$.data.offerings[0].offeringKey")
                        .value("platform-codex-cli"))
                .andExpect(jsonPath("$.data.offerings[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.data.users[0].id").value(8))
                .andExpect(jsonPath("$.data.users[0].username").value("amy"))
                .andExpect(jsonPath("$.data.users[0].nickname").value("Amy Alias"))
                .andExpect(jsonPath("$.data.users[0].role").value("USER"))
                .andExpect(jsonPath("$.data.users[0].banned").value(true))
                .andExpect(jsonPath("$.data.users[0].cliAccess.codex").value(true))
                .andExpect(jsonPath("$.data.users[0].offeringAccess.platform-codex-cli")
                        .value(true))
                .andExpect(jsonPath("$.data.users[0].codexAccess").doesNotExist())
                .andExpect(jsonPath("$.data.users[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.users[0].deleted").doesNotExist());

        assertEquals("Amy", fixture.userMapper.lastSearchQuery);
        assertEquals(100, fixture.userMapper.lastSearchLimit);
    }

    @Test
    void runtimeOfflineStillReturnsDatabaseUsersClisOfferingsAndAccess() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));
        when(fixture.runtimeClient.listClis()).thenThrow(new AdminRuntimeUnavailableException());

        fixture.mvc.perform(get("/api/admin/users").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeAvailable").value(false))
                .andExpect(jsonPath("$.data.clis[0].runtimeStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.offerings[0].offeringKey")
                        .value("platform-codex-cli"))
                .andExpect(jsonPath("$.data.offerings[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.data.users[0].id").value(7))
                .andExpect(jsonPath("$.data.users[1].id").value(8))
                .andExpect(jsonPath("$.data.users[1].cliAccess.codex").value(false))
                .andExpect(jsonPath("$.data.users[1].offeringAccess.platform-codex-cli")
                        .value(false));

        assertEquals(1, fixture.userMapper.searchCalls);
        assertEquals(1, fixture.platformMapper.enabledOfferingReads);
        assertEquals(1, fixture.platformMapper.overrideReads);
    }

    @Test
    void adminCanPromoteUser() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/role")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"ADMIN"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("ADMIN", fixture.userMapper.findById(8L).getRole());
    }

    @Test
    void invalidRoleReturnsBadRequest() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/role")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"OWNER"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40008));
    }

    @Test
    void missingOrDeletedUserReturnsNotFound() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "gone", "Gone", "USER", 1, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled":true}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void cliEndpointUsesValidatedCliKey() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(
                List.of(new CliWrite(8L, "platform-codex-cli", true)),
                fixture.cliWrites);
    }

    @Test
    void codexEndpointRejectsMissingEnabledWithoutUpsert() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        assertTrue(fixture.cliWrites.isEmpty());
    }

    @Test
    void codexEndpointRejectsFeatureKeyWithoutUpsert() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featureKey\":\"feature.tts\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(fixture.cliWrites.isEmpty());
    }

    @Test
    void codexEndpointRejectsFeatureKeyAlongsideEnabledWithoutUpsert() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"featureKey\":\"feature.tts\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(fixture.cliWrites.isEmpty());
    }

    @Test
    void codexEndpointRejectsNonBooleanEnabledWithoutUpsert() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/cli-access/codex")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":\"true\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(fixture.cliWrites.isEmpty());
    }

    @Test
    void adminBanAndDeleteUseSessionActorIdentity() throws Exception {
        Fixture fixture = fixture("ADMIN");
        fixture.userMapper.add(user(9L, "other-admin", "Other Admin", "ADMIN", 0, false));

        fixture.mvc.perform(put("/api/admin/users/8/banned")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"banned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        fixture.mvc.perform(delete("/api/admin/users/8")
                .queryParam("actorUserId", "999")
                .session(session(9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(List.of(new BanWrite(7L, 8L, true)), fixture.banWrites);
        assertEquals(List.of(new DeleteWrite(9L, 8L)), fixture.deleteWrites);
    }

    @Test
    void bannedEndpointRequiresExactlyOneBooleanField() throws Exception {
        Fixture fixture = fixture("ADMIN");
        List<String> invalidBodies = List.of(
                "{}",
                "null",
                "{\"banned\":\"true\"}",
                "{\"banned\":true,\"actorUserId\":999}",
                "{\"banned\":true,\"extra\":false}");

        for (String body : invalidBodies) {
            fixture.mvc.perform(put("/api/admin/users/8/banned")
                    .session(session(7L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40009));
        }

        assertTrue(fixture.banWrites.isEmpty());
    }

    @Test
    void missingBannedBodyUsesInvalidRequestError() throws Exception {
        Fixture fixture = fixture("ADMIN");

        fixture.mvc.perform(put("/api/admin/users/8/banned")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));

        assertTrue(fixture.banWrites.isEmpty());
    }

    @Test
    void accountBusinessErrorsKeepSafeAdminCodes() throws Exception {
        Fixture fixture = fixture("ADMIN");
        doThrow(new openflash_admin.common.AdminException(
                openflash_admin.common.AdminErrorCode.LAST_ADMIN_REQUIRED))
                .doThrow(new openflash_admin.common.AdminException(
                        openflash_admin.common.AdminErrorCode.SELF_ACCOUNT_MUTATION))
                .when(fixture.coreClient).setUserBanned(7L, 8L, true);

        fixture.mvc.perform(put("/api/admin/users/8/banned")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"banned\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        fixture.mvc.perform(put("/api/admin/users/8/banned")
                .session(session(7L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"banned\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void ordinaryUserSessionCannotCallAnyUserEndpoint() throws Exception {
        Fixture fixture = fixture("USER");
        fixture.userMapper.add(user(8L, "amy", "Amy", "USER", 0, false));
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/admin/users"),
                put("/api/admin/users/8/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"),
                put("/api/admin/users/8/banned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banned\":true}"),
                delete("/api/admin/users/8"),
                put("/api/admin/users/8/cli-access/codex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"));

        for (MockHttpServletRequestBuilder request : requests) {
            fixture.mvc.perform(request.session(session(7L)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
        }

        assertEquals(0, fixture.userMapper.searchCalls);
        assertEquals(0, fixture.userMapper.updateRoleCalls);
        assertTrue(fixture.cliWrites.isEmpty());
        assertTrue(fixture.banWrites.isEmpty());
        assertTrue(fixture.deleteWrites.isEmpty());
    }

    private static Fixture fixture(String sessionUserRole) {
        InMemoryAdminUserMapper userMapper = new InMemoryAdminUserMapper();
        userMapper.add(user(7L, "session", "Session User", sessionUserRole, 0, false));
        RecordingPlatformAiMapper platformMapper = new RecordingPlatformAiMapper();
        AdminUserServiceImpl userService = new AdminUserServiceImpl(userMapper);
        AiRuntimeAdminClient runtimeClient = mock(AiRuntimeAdminClient.class);
        OpenFlashCoreAdminClient coreClient = mock(OpenFlashCoreAdminClient.class);
        List<CliWrite> cliWrites = new ArrayList<>();
        List<BanWrite> banWrites = new ArrayList<>();
        List<DeleteWrite> deleteWrites = new ArrayList<>();
        when(runtimeClient.listClis()).thenReturn(List.of(
                new CliSnapshot(
                        "codex", "platform-codex", "platform-codex-cli", "AVAILABLE")));
        doAnswer(invocation -> {
            cliWrites.add(new CliWrite(
                    invocation.getArgument(1),
                    invocation.getArgument(0),
                    ((SetUserAccessRequest) invocation.getArgument(2)).enabled()));
            return null;
        }).when(runtimeClient).setUserAccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SetUserAccessRequest.class));
        doAnswer(invocation -> {
            banWrites.add(new BanWrite(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
            return null;
        }).when(coreClient).setUserBanned(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean());
        doAnswer(invocation -> {
            deleteWrites.add(new DeleteWrite(
                    invocation.getArgument(0),
                    invocation.getArgument(1)));
            return null;
        }).when(coreClient).deleteUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
        AdminCliAccessServiceImpl cliAccessService = new AdminCliAccessServiceImpl(
                userMapper, platformMapper, runtimeClient);
        AdminSessionService sessionService = new AdminSessionServiceImpl(
                userMapper, new PasswordVerifier(), AdminLoginAttemptGuard.fromConfigLoader(key -> null));
        AdminUserAccountServiceImpl accountService = new AdminUserAccountServiceImpl(
                sessionService, coreClient);
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(
                        userService, cliAccessService, accountService))
                .addMappedInterceptors(
                        new String[] { "/api/admin/**" },
                        new AdminAuthInterceptor(sessionService))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
        return new Fixture(
                mvc, userMapper, platformMapper, runtimeClient, coreClient,
                cliWrites, banWrites, deleteWrites);
    }

    private static MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminUserId", userId);
        session.setAttribute("adminAuthVersion", 0L);
        return session;
    }

    private static AdminUser user(
            Long id,
            String username,
            String nickname,
            String role,
            int deleted,
            boolean codexAccess) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("must-never-reach-json");
        user.setNickname(nickname);
        user.setRole(role);
        user.setAdminApproved("ADMIN".equals(role));
        user.setBanned(0);
        user.setAuthVersion(0L);
        user.setDeleted(deleted);
        return user;
    }

    private record Fixture(
            MockMvc mvc,
            InMemoryAdminUserMapper userMapper,
            RecordingPlatformAiMapper platformMapper,
            AiRuntimeAdminClient runtimeClient,
            OpenFlashCoreAdminClient coreClient,
            List<CliWrite> cliWrites,
            List<BanWrite> banWrites,
            List<DeleteWrite> deleteWrites) {
    }

    private static final class InMemoryAdminUserMapper implements AdminUserMapper {

        private final Map<Long, AdminUser> users = new LinkedHashMap<>();
        private int searchCalls;
        private int updateRoleCalls;
        private String lastSearchQuery;
        private int lastSearchLimit;

        private void add(AdminUser user) {
            users.put(user.getId(), user);
        }

        @Override
        public AdminUser findByUsername(String username) {
            return users.values().stream()
                    .filter(user -> user.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public AdminUser findById(Long id) {
            return users.get(id);
        }

        @Override
        public AdminUser lockById(Long id) {
            AdminUser user = users.get(id);
            return user != null && Integer.valueOf(0).equals(user.getDeleted()) ? user : null;
        }

        @Override
        public List<AdminUser> search(String query, int limit) {
            searchCalls++;
            lastSearchQuery = query;
            lastSearchLimit = limit;
            String normalized = query.toLowerCase(Locale.ROOT);
            return users.values().stream()
                    .filter(user -> Integer.valueOf(0).equals(user.getDeleted()))
                    .filter(user -> normalized.isEmpty()
                            || user.getUsername().toLowerCase(Locale.ROOT).contains(normalized)
                            || user.getNickname().toLowerCase(Locale.ROOT).contains(normalized))
                    .sorted((left, right) -> left.getId().compareTo(right.getId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Long> lockActiveAdminIds() {
            return users.values().stream()
                    .filter(user -> Integer.valueOf(0).equals(user.getDeleted()))
                    .filter(user -> "ADMIN".equals(user.getRole()))
                    .filter(user -> Boolean.TRUE.equals(user.getAdminApproved()))
                    .filter(user -> Integer.valueOf(0).equals(user.getBanned()))
                    .map(AdminUser::getId)
                    .toList();
        }

        @Override
        public int updateRole(Long userId, String role) {
            updateRoleCalls++;
            AdminUser user = users.get(userId);
            if (user == null || !Integer.valueOf(0).equals(user.getDeleted())) {
                return 0;
            }
            user.setRole(role);
            user.setAdminApproved("ADMIN".equals(role));
            return 1;
        }

    }

    private static final class RecordingPlatformAiMapper implements AdminPlatformAiMapper {

        private final List<UserAccessOverrideRow> overrides = new ArrayList<>();
        private int enabledOfferingReads;
        private int overrideReads;

        @Override
        public List<AdminPlatformAiMapper.CatalogRow> findCatalogRows() {
            return List.of();
        }

        @Override
        public List<EnabledOfferingRow> findEnabledOfferings() {
            enabledOfferingReads++;
            return List.of(new EnabledOfferingRow(
                    21L, "platform-codex-cli", null, false, 0,
                    "platform-codex", "CLI", "CODEX_APP_SERVER", "codex"));
        }

        @Override
        public List<UserAccessOverrideRow> findUserAccessOverrides(List<Long> userIds) {
            overrideReads++;
            return overrides.stream().filter(row -> userIds.contains(row.userId())).toList();
        }

        @Override
        public EnabledOfferingRow findEnabledOfferingByKey(String offeringKey) {
            return "platform-codex-cli".equals(offeringKey)
                    ? findEnabledOfferings().get(0)
                    : null;
        }

        @Override
        public AdminPlatformAiMapper.CatalogRow findCliOffering(String cliKey) {
            return null;
        }
    }

    private record CliWrite(Long userId, String cliKey, boolean enabled) {
    }

    private record BanWrite(Long actorUserId, Long userId, boolean banned) {
    }

    private record DeleteWrite(Long actorUserId, Long userId) {
    }
}
