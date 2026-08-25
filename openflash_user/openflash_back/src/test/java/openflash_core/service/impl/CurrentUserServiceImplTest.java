package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.User;
import openflash_core.mapper.UserMapper;
import openflash_core.mapper.UserSettingsMapper;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSettingsMapper userSettingsMapper;

    private MockHttpServletRequest request;
    private CurrentUserServiceImpl service;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        service = new CurrentUserServiceImpl(userMapper, userSettingsMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void bannedSessionUserIsLoggedOutAndRejected() {
        User user = user(8L, 1);
        when(userMapper.findById(8L)).thenReturn(user);
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute("currentUserId", 8L);

        AppException error = assertThrows(AppException.class, service::getCurrentUser);

        assertEquals(ErrorCode.ACCOUNT_BANNED, error.getErrorCode());
        assertTrue(session.isInvalid());
    }

    @Test
    void sessionUserWithUnknownBanStateIsLoggedOutAndRejected() {
        User user = user(8L, null);
        when(userMapper.findById(8L)).thenReturn(user);
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute("currentUserId", 8L);

        AppException error = assertThrows(AppException.class, service::getCurrentUser);

        assertEquals(ErrorCode.ACCOUNT_BANNED, error.getErrorCode());
        assertTrue(session.isInvalid());
    }

    @Test
    void deletedSessionUserIsLoggedOutWithDeletedCode() {
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute("currentUserId", 8L);
        session.setAttribute("currentAuthVersion", 3L);

        AppException error = assertThrows(AppException.class, service::getCurrentUser);

        assertEquals(ErrorCode.ACCOUNT_DELETED, error.getErrorCode());
        assertTrue(session.isInvalid());
    }

    @Test
    void oldSessionDoesNotReviveAfterUserIsUnbanned() {
        User user = user(8L, 0, 2L);
        when(userMapper.findById(8L)).thenReturn(user);
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute("currentUserId", 8L);
        session.setAttribute("currentAuthVersion", 1L);

        AppException error = assertThrows(AppException.class, service::getCurrentUser);

        assertEquals(ErrorCode.SESSION_EXPIRED, error.getErrorCode());
        assertTrue(session.isInvalid());
    }

    @Test
    void loginStoresCurrentAuthVersionInSession() {
        User user = user(8L, 0, 4L);

        service.login(user);

        MockHttpSession session = (MockHttpSession) request.getSession(false);
        assertEquals(8L, session.getAttribute("currentUserId"));
        assertEquals(4L, session.getAttribute("currentAuthVersion"));
    }

    private static User user(Long id, Integer banned) {
        return user(id, banned, 0L);
    }

    private static User user(Long id, Integer banned, Long authVersion) {
        User user = new User();
        user.setId(id);
        user.setBanned(banned);
        user.setAuthVersion(authVersion);
        user.setDeleted(0);
        return user;
    }
}
