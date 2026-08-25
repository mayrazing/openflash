package openflash_admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import openflash_admin.common.AdminExceptionHandler;
import openflash_admin.entity.AdminUser;
import openflash_admin.interceptor.AdminAuthInterceptor;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.security.PasswordVerifier;
import openflash_admin.security.AdminLoginAttemptGuard;
import openflash_admin.service.AdminSessionService;
import openflash_admin.service.impl.AdminSessionServiceImpl;

class AdminAuthControllerTest {

    private static final String PASSWORD_HASH =
        new BCryptPasswordEncoder(4).encode("long-password");

    @Test
    void validAdminLogsInAndReturnsSafeAdminFields() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));

        MvcResult result = fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.username").value("root"))
            .andExpect(jsonPath("$.data.nickname").value("Root Admin"))
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertEquals(7L, session.getAttribute("adminUserId"));
        assertEquals(0L, session.getAttribute("adminAuthVersion"));
    }

    @Test
    void validAdminLoginReplacesAUserSessionWithoutCopyingItsAttributes() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute("currentUserId", 99L);
        String oldSessionId = userSession.getId();

        MvcResult result = fixture.mvc.perform(post("/api/admin/auth/login")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession adminSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(adminSession);
        assertNotSame(userSession, adminSession);
        assertNotEquals(oldSessionId, adminSession.getId());
        assertEquals(7L, adminSession.getAttribute("adminUserId"));
        assertNull(adminSession.getAttribute("currentUserId"));
        assertTrue(userSession.isInvalid());

        MockHttpSession replayedOldId = new MockHttpSession(null, oldSessionId);
        fixture.mvc.perform(get("/api/admin/auth/me").session(replayedOldId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void wrongPasswordDoesNotCreateOrRotateAnExistingUserSession() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute("currentUserId", 99L);
        String sessionId = userSession.getId();

        MvcResult result = fixture.mvc.perform(post("/api/admin/auth/login")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"wrong"}
                    """))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertSameSession(result, userSession, sessionId);
    }

    @Test
    void nonAdminLoginDoesNotCreateOrRotateAnExistingUserSession() throws Exception {
        Fixture fixture = fixture(user("USER"));
        MockHttpSession userSession = new MockHttpSession();
        userSession.setAttribute("currentUserId", 99L);
        String sessionId = userSession.getId();

        MvcResult result = fixture.mvc.perform(post("/api/admin/auth/login")
                .session(userSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isForbidden())
            .andReturn();

        assertSameSession(result, userSession, sessionId);
    }

    @Test
    void validNonAdminGetsForbiddenWithoutSession() throws Exception {
        Fixture fixture = fixture(user("USER"));

        MvcResult result = fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301))
            .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void rootRoleWithoutExplicitApprovalGetsForbidden() throws Exception {
        AdminUser user = user("ADMIN");
        user.setAdminApproved(false);
        Fixture fixture = fixture(user);

        fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void wrongPasswordGetsCompatibleCredentialsError() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));

        fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"wrong"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void repeatedWrongAdminPasswordsAreRateLimited() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        for (int attempt = 0; attempt < 5; attempt++) {
            fixture.mvc.perform(post("/api/admin/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"root","password":"wrong"}
                        """))
                .andExpect(status().isBadRequest());
        }

        fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"wrong"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(42902));
    }

    @Test
    void bannedAdminGetsCompatibleCredentialsError() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        fixture.mapper.user.setBanned(1);

        fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void adminWithUnknownBanStateGetsCompatibleCredentialsError() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        fixture.mapper.user.setBanned(null);

        fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void meWithoutAdminSessionGetsUnauthorized() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));

        fixture.mvc.perform(get("/api/admin/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void roleChangedAfterLoginMakesNextProtectedRequestForbidden() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);
        fixture.mapper.user.setRole("USER");

        fixture.mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void revokedAdminApprovalInvalidatesExistingAdminSession() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);
        fixture.mapper.user.setAdminApproved(false);

        fixture.mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40301));

        assertTrue(session.isInvalid());
    }

    @Test
    void passwordVersionChangeInvalidatesExistingAdminSession() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);
        fixture.mapper.user.setAuthVersion(1L);

        fixture.mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));

        assertTrue(session.isInvalid());
    }

    @Test
    void bannedAdminSessionGetsUnauthorized() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);
        fixture.mapper.user.setBanned(1);

        fixture.mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));

        assertTrue(session.isInvalid());
    }

    @Test
    void adminSessionWithUnknownBanStateGetsUnauthorized() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);
        fixture.mapper.user.setBanned(null);

        fixture.mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));

        assertTrue(session.isInvalid());
    }

    @Test
    void logoutInvalidatesSessionConfiguredWithAdminCookieName() throws Exception {
        Fixture fixture = fixture(user("ADMIN"));
        MockHttpSession session = login(fixture);

        fixture.mvc.perform(post("/api/admin/auth/logout").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertTrue(session.isInvalid());
        String yaml = applicationYaml();
        assertTrue(yaml.contains("name: OPENFLASH_ADMIN_SESSION"));
        assertFalse(yaml.contains("name: JSESSIONID"));
    }

    private static MockHttpSession login(Fixture fixture) throws Exception {
        MvcResult login = fixture.mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"root","password":"long-password"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static void assertSameSession(
        MvcResult result,
        MockHttpSession expected,
        String expectedId
    ) {
        MockHttpSession actual = (MockHttpSession) result.getRequest().getSession(false);
        assertSame(expected, actual);
        assertEquals(expectedId, actual.getId());
        assertEquals(99L, actual.getAttribute("currentUserId"));
        assertNull(actual.getAttribute("adminUserId"));
        assertFalse(actual.isInvalid());
    }

    private static Fixture fixture(AdminUser user) {
        MutableAdminUserMapper mapper = new MutableAdminUserMapper(user);
        AdminSessionService sessionService = new AdminSessionServiceImpl(
            mapper, new PasswordVerifier(), AdminLoginAttemptGuard.fromConfigLoader(key -> null));
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AdminAuthController(sessionService))
            .addMappedInterceptors(
                new String[] {"/api/admin/auth/me"},
                new AdminAuthInterceptor(sessionService))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();
        return new Fixture(mvc, mapper);
    }

    private static AdminUser user(String role) {
        AdminUser user = new AdminUser();
        user.setId(7L);
        user.setUsername("root");
        user.setPasswordHash(PASSWORD_HASH);
        user.setNickname("Root Admin");
        user.setRole(role);
        user.setAdminApproved("ADMIN".equals(role));
        user.setBanned(0);
        user.setAuthVersion(0L);
        user.setDeleted(0);
        return user;
    }

    private static String applicationYaml() throws IOException {
        return new ClassPathResource("application.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    private record Fixture(MockMvc mvc, MutableAdminUserMapper mapper) {
    }

    private static final class MutableAdminUserMapper implements AdminUserMapper {

        private final AdminUser user;

        private MutableAdminUserMapper(AdminUser user) {
            this.user = user;
        }

        @Override
        public AdminUser findByUsername(String username) {
            return user.getUsername().equals(username) ? user : null;
        }

        @Override
        public AdminUser findById(Long id) {
            return user.getId().equals(id) ? user : null;
        }

        @Override
        public AdminUser lockById(Long id) {
            return findById(id);
        }

        @Override
        public List<AdminUser> search(String query, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> lockActiveAdminIds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateRole(Long userId, String role) {
            throw new UnsupportedOperationException();
        }

    }
}
