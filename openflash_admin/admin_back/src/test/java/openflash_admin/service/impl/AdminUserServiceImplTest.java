package openflash_admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.entity.AdminUser;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.service.AdminUserService;

class AdminUserServiceImplTest {

    @Test
    void blankQueryUsesEmptySearchAndHardMaximumOfOneHundred() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser first = user(1L, "alpha", "Alpha", "USER", 0);
        AdminUser second = user(2L, "beta", "Beta", "ADMIN", 0);
        userMapper.searchResults = List.of(first, second);
        AdminUserService service = service(userMapper);

        List<AdminUser> result = service.search("   ");

        assertEquals("", userMapper.lastSearchQuery);
        assertEquals(100, userMapper.lastSearchLimit);
        assertEquals(List.of(first, second), result);
    }

    @Test
    void queryIsPassedToUsernameNicknameSearchWithHardMaximumOfOneHundred() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUserService service = service(userMapper);

        service.search("amy");

        assertEquals("amy", userMapper.lastSearchQuery);
        assertEquals(100, userMapper.lastSearchLimit);
    }

    @Test
    void searchSqlReturnsOnlyActiveSafeFieldsInIdOrder() throws IOException {
        String sql = mapperStatement("mapper/AdminUserMapper.xml", "search");

        assertTrue(sql.contains("select u.id, u.username, u.nickname, u.role"));
        assertFalse(sql.contains("codex_access"));
        assertTrue(sql.contains("where u.deleted=0"));
        assertTrue(sql.contains("u.username ilike concat('%', #{query}, '%')"));
        assertTrue(sql.contains("u.nickname ilike concat('%', #{query}, '%')"));
        assertTrue(sql.contains("order by u.id"));
        assertTrue(sql.contains("limit #{limit}"));
        assertFalse(sql.contains("password"));
    }

    @Test
    void roleAcceptsOnlyAdminOrUser() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        userMapper.add(user(7L, "root", "Root", "ADMIN", 0));
        AdminUserService service = service(userMapper);

        for (String role : new String[] { null, "", "admin", "OWNER" }) {
            AdminException exception = assertThrows(
                    AdminException.class,
                    () -> service.updateRole(7L, role));
            assertEquals(AdminErrorCode.INVALID_ROLE, exception.getErrorCode());
        }
        assertEquals(0, userMapper.updateRoleCalls);
    }

    @Test
    void roleMutationLocksAdminSetAndTargetBeforeParsingRole() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUserService service = service(userMapper);

        AdminException exception = assertThrows(
                AdminException.class,
                () -> service.updateRole(99L, "OWNER"));

        assertEquals(AdminErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        assertEquals(List.of("lockActiveAdminIds", "lockById"), userMapper.events);
        assertEquals(0, userMapper.findByIdCalls);
    }

    @Test
    void promotingUserLocksAdminSetThenTargetBeforeUpdate() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(8L, "amy", "Amy", "USER", 0);
        userMapper.add(target);
        AdminUserService service = service(userMapper);

        service.updateRole(8L, "ADMIN");

        assertEquals("ADMIN", target.getRole());
        assertTrue(target.getAdminApproved());
        assertEquals(1, userMapper.updateRoleCalls);
        assertEquals(
                List.of("lockActiveAdminIds", "lockById", "updateRole"),
                userMapper.events);
        assertEquals(0, userMapper.findByIdCalls);
    }

    @Test
    void demotingLastActiveAdminFailsWithoutUpdate() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(7L, "root", "Root", "ADMIN", 0);
        userMapper.add(target);
        AdminUserService service = service(userMapper);

        AdminException exception = assertThrows(
                AdminException.class,
                () -> service.updateRole(7L, "USER"));

        assertEquals(AdminErrorCode.LAST_ADMIN_REQUIRED, exception.getErrorCode());
        assertEquals("ADMIN", target.getRole());
        assertEquals(1, userMapper.lockCalls);
        assertEquals(0, userMapper.updateRoleCalls);
    }

    @Test
    void demotionLocksActiveAdminsBeforeCountingAndUpdating() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(7L, "root", "Root", "ADMIN", 0);
        userMapper.add(target);
        userMapper.add(user(9L, "backup", "Backup", "ADMIN", 0));
        AdminUserService service = service(userMapper);

        service.updateRole(7L, "USER");

        assertEquals(
                List.of("lockActiveAdminIds", "lockById", "updateRole"),
                userMapper.events);
        assertEquals("USER", target.getRole());
    }

    @Test
    void bannedAdminCanBeDemotedWithoutReducingAvailableAdminSet() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(7L, "root", "Root", "ADMIN", 0);
        target.setBanned(1);
        userMapper.add(target);
        userMapper.add(user(9L, "backup", "Backup", "ADMIN", 0));
        AdminUserService service = service(userMapper);

        service.updateRole(7L, "USER");

        assertEquals("USER", target.getRole());
        assertEquals(
                List.of("lockActiveAdminIds", "lockById", "updateRole"),
                userMapper.events);
    }

    @Test
    void activeAdminMissingFromLockedSnapshotFailsClosed() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(7L, "root", "Root", "ADMIN", 0);
        userMapper.add(target);
        userMapper.add(user(9L, "backup", "Backup", "ADMIN", 0));
        userMapper.lockedActiveAdminIds = List.of(9L);
        AdminUserService service = service(userMapper);

        AdminException exception = assertThrows(
                AdminException.class,
                () -> service.updateRole(7L, "USER"));

        assertEquals(AdminErrorCode.LAST_ADMIN_REQUIRED, exception.getErrorCode());
        assertEquals(List.of("lockActiveAdminIds", "lockById"), userMapper.events);
        assertEquals(0, userMapper.updateRoleCalls);
    }

    @Test
    void idempotentRoleMutationStillLocksAdminSetThenTarget() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        userMapper.add(user(7L, "root", "Root", "ADMIN", 0));
        AdminUserService service = service(userMapper);

        service.updateRole(7L, "ADMIN");

        assertEquals(List.of("lockActiveAdminIds", "lockById"), userMapper.events);
        assertEquals(0, userMapper.updateRoleCalls);
    }

    @Test
    void existingRootRoleNeedsExplicitApprovalWrite() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        AdminUser target = user(7L, "root", "Root", "ADMIN", 0);
        target.setAdminApproved(false);
        userMapper.add(target);
        userMapper.add(user(9L, "backup", "Backup", "ADMIN", 0));
        AdminUserService service = service(userMapper);

        service.updateRole(7L, "ADMIN");

        assertEquals(1, userMapper.updateRoleCalls);
        assertTrue(target.getAdminApproved());
    }

    @Test
    void abnormalRoleUpdateCountReturnsNotFound() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        userMapper.add(user(8L, "amy", "Amy", "USER", 0));
        userMapper.nextUpdateRoleCount = 2;
        AdminUserService service = service(userMapper);

        AdminException exception = assertThrows(
                AdminException.class,
                () -> service.updateRole(8L, "ADMIN"));

        assertEquals(AdminErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void roleLockSqlUsesAvailableAdminSnapshotAndCompleteOwnerProjection() throws IOException {
        String lockAdmins = mapperStatement("mapper/AdminUserMapper.xml", "lockActiveAdminIds");
        String lockTarget = mapperStatement("mapper/AdminUserMapper.xml", "lockById");

        assertTrue(lockAdmins.contains("role='admin'"));
        assertTrue(lockAdmins.contains("deleted=0"));
        assertTrue(lockAdmins.contains("banned=0"));
        assertTrue(lockAdmins.contains("admin_approved=1"));
        assertTrue(lockAdmins.contains("order by id"));
        assertTrue(lockAdmins.contains("for update"));
        assertTrue(lockTarget.contains("id, username, password_hash, nickname, role, banned"));
        assertTrue(lockTarget.contains("admin_approved"));
        assertTrue(lockTarget.contains("created_at, updated_at, deleted"));
        assertTrue(lockTarget.contains("where id = #{id} and deleted = 0"));
        assertTrue(lockTarget.contains("for update"));
    }

    @Test
    void roleMutationIsTransactional() throws NoSuchMethodException {
        Transactional annotation = AdminUserServiceImpl.class
                .getMethod("updateRole", Long.class, String.class)
                .getAnnotation(Transactional.class);

        assertTrue(annotation != null);
    }

    @Test
    void missingOrDeletedRoleTargetReturnsNotFound() {
        RecordingAdminUserMapper userMapper = new RecordingAdminUserMapper();
        userMapper.add(user(8L, "gone", "Gone", "USER", 1));
        AdminUserService service = service(userMapper);

        for (Long userId : List.of(8L, 99L)) {
            AdminException exception = assertThrows(
                    AdminException.class,
                    () -> service.updateRole(userId, "ADMIN"));
            assertEquals(AdminErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
        assertEquals(0, userMapper.updateRoleCalls);
    }

    private static AdminUserService service(RecordingAdminUserMapper userMapper) {
        return new AdminUserServiceImpl(userMapper);
    }

    private static AdminUser user(
            Long id,
            String username,
            String nickname,
            String role,
            int deleted) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("must-never-reach-json");
        user.setNickname(nickname);
        user.setRole(role);
        user.setAdminApproved("ADMIN".equals(role));
        user.setBanned(0);
        user.setDeleted(deleted);
        return user;
    }

    private static String mapperStatement(String resourcePath, String statementId)
            throws IOException {
        String xml = new ClassPathResource(resourcePath)
                .getContentAsString(StandardCharsets.UTF_8);
        String lower = xml.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("id=\"" + statementId.toLowerCase(Locale.ROOT) + "\"");
        assertTrue(start >= 0, "Missing mapper statement " + statementId);
        int bodyStart = lower.indexOf('>', start) + 1;
        int end = lower.indexOf("</", bodyStart);
        return lower.substring(bodyStart, end).replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingAdminUserMapper implements AdminUserMapper {

        private final Map<Long, AdminUser> users = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private List<AdminUser> searchResults;
        private String lastSearchQuery;
        private int lastSearchLimit;
        private int lockCalls;
        private int findByIdCalls;
        private int updateRoleCalls;
        private int nextUpdateRoleCount = 1;
        private List<Long> lockedActiveAdminIds;

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
            findByIdCalls++;
            return users.get(id);
        }

        @Override
        public AdminUser lockById(Long id) {
            events.add("lockById");
            AdminUser user = users.get(id);
            return user != null && Integer.valueOf(0).equals(user.getDeleted()) ? user : null;
        }

        @Override
        public List<AdminUser> search(String query, int limit) {
            lastSearchQuery = query;
            lastSearchLimit = limit;
            if (searchResults != null) {
                return searchResults;
            }
            String normalized = query.toLowerCase(Locale.ROOT);
            return users.values().stream()
                    .filter(user -> Integer.valueOf(0).equals(user.getDeleted()))
                    .filter(user -> normalized.isEmpty()
                            || user.getUsername().toLowerCase(Locale.ROOT).contains(normalized)
                            || user.getNickname().toLowerCase(Locale.ROOT).contains(normalized))
                    .sorted(Comparator.comparing(AdminUser::getId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Long> lockActiveAdminIds() {
            events.add("lockActiveAdminIds");
            lockCalls++;
            if (lockedActiveAdminIds != null) {
                return lockedActiveAdminIds;
            }
            return users.values().stream()
                    .filter(user -> Integer.valueOf(0).equals(user.getDeleted()))
                    .filter(user -> "ADMIN".equals(user.getRole()))
                    .filter(user -> Integer.valueOf(0).equals(user.getBanned()))
                    .filter(user -> Boolean.TRUE.equals(user.getAdminApproved()))
                    .map(AdminUser::getId)
                    .sorted()
                    .toList();
        }

        @Override
        public int updateRole(Long userId, String role) {
            events.add("updateRole");
            updateRoleCalls++;
            if (nextUpdateRoleCount != 1) {
                return nextUpdateRoleCount;
            }
            AdminUser user = users.get(userId);
            if (user == null || !Integer.valueOf(0).equals(user.getDeleted())) {
                return 0;
            }
            user.setRole(role);
            user.setAdminApproved("ADMIN".equals(role));
            return 1;
        }

    }

}
