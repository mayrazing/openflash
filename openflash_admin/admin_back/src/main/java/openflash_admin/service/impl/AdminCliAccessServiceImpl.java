package openflash_admin.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.CliSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.dto.OfferingAccessMetadata;
import openflash_admin.dto.UserAccess;
import openflash_admin.dto.UserAccessPage;
import openflash_admin.entity.AdminUser;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.EnabledOfferingRow;
import openflash_admin.mapper.AdminPlatformAiMapper.UserAccessOverrideRow;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.service.AdminCliAccessService;
import org.springframework.stereotype.Service;

/** 用两次批量查询组成用户平台权限, runtime 只补充 CLI 实时状态和执行写入. */
@Service
public class AdminCliAccessServiceImpl implements AdminCliAccessService {

    private static final int MAX_USERS = 100;

    private final AdminUserMapper userMapper;
    private final AdminPlatformAiMapper platformMapper;
    private final AiRuntimeAdminClient runtimeClient;

    public AdminCliAccessServiceImpl(
            AdminUserMapper userMapper,
            AdminPlatformAiMapper platformMapper,
            AiRuntimeAdminClient runtimeClient) {
        this.userMapper = userMapper;
        this.platformMapper = platformMapper;
        this.runtimeClient = runtimeClient;
    }

    @Override
    public UserAccessPage accessForUsers(List<AdminUser> users) {
        if (users == null || users.size() > MAX_USERS) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        List<EnabledOfferingRow> offerings = List.copyOf(platformMapper.findEnabledOfferings());
        List<Long> userIds = users.stream().map(AdminUser::getId).toList();
        List<UserAccessOverrideRow> overrides = userIds.isEmpty()
            ? List.of()
            : List.copyOf(platformMapper.findUserAccessOverrides(userIds));
        Map<AccessKey, Boolean> overrideByKey = indexOverrides(overrides);

        boolean runtimeAvailable;
        List<CliSnapshot> runtimeClis;
        try {
            runtimeClis = runtimeClient.listClis();
            runtimeAvailable = true;
        } catch (AdminRuntimeUnavailableException offline) {
            runtimeClis = List.of();
            runtimeAvailable = false;
        }

        Map<String, CliSnapshot> runtimeByOffering = indexRuntimeClis(runtimeClis);
        List<CliSnapshot> clis = offerings.stream()
            .filter(AdminCliAccessServiceImpl::isCli)
            .map(offering -> cliSnapshot(offering, runtimeByOffering.get(offering.offeringKey())))
            .toList();
        List<OfferingAccessMetadata> metadata = offerings.stream()
            .map(AdminCliAccessServiceImpl::metadata)
            .toList();

        Map<Long, UserAccess> accessByUser = new LinkedHashMap<>();
        for (AdminUser user : users) {
            Map<String, Boolean> offeringAccess = new LinkedHashMap<>();
            Map<String, Boolean> cliAccess = new LinkedHashMap<>();
            for (EnabledOfferingRow offering : offerings) {
                Boolean override = overrideByKey.get(
                    new AccessKey(user.getId(), offering.offeringKey()));
                boolean resolved = override != null ? override : offering.defaultAccess();
                offeringAccess.put(offering.offeringKey(), resolved);
                if (isCli(offering)) cliAccess.put(offering.cliKey(), resolved);
            }
            if (accessByUser.put(
                    user.getId(),
                    new UserAccess(immutable(cliAccess), immutable(offeringAccess))) != null) {
                throw new IllegalStateException("duplicate admin user id");
            }
        }
        return new UserAccessPage(
            runtimeAvailable,
            clis,
            metadata,
            Collections.unmodifiableMap(accessByUser));
    }

    @Override
    public void updateAccess(Long userId, String cliKey, boolean enabled) {
        AdminUser user = userMapper.findById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getDeleted())) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        CliSnapshot cli = runtimeClient.listClis().stream()
            .filter(candidate -> candidate.cliKey().equals(cliKey))
            .findFirst()
            .orElseThrow(AdminCliAccessServiceImpl::invalidRequest);
        EnabledOfferingRow offering = platformMapper.findEnabledOfferingByKey(cli.offeringKey());
        if (offering == null
                || !isCli(offering)
                || !offering.cliKey().equals(cli.cliKey())
                || !offering.connectionKey().equals(cli.connectionKey())) {
            throw invalidRequest();
        }
        runtimeClient.setUserAccess(
            offering.offeringKey(), userId, new SetUserAccessRequest(enabled));
    }

    private static Map<AccessKey, Boolean> indexOverrides(
            List<UserAccessOverrideRow> overrides) {
        Map<AccessKey, Boolean> indexed = new LinkedHashMap<>();
        for (UserAccessOverrideRow override : overrides) {
            AccessKey key = new AccessKey(override.userId(), override.offeringKey());
            if (indexed.put(key, override.enabled()) != null) {
                throw new IllegalStateException("duplicate platform access override");
            }
        }
        return indexed;
    }

    private static Map<String, CliSnapshot> indexRuntimeClis(List<CliSnapshot> clis) {
        Map<String, CliSnapshot> indexed = new LinkedHashMap<>();
        for (CliSnapshot cli : clis) {
            if (indexed.put(cli.offeringKey(), cli) != null) {
                throw new IllegalStateException("duplicate runtime CLI offering");
            }
        }
        return indexed;
    }

    private static CliSnapshot cliSnapshot(
            EnabledOfferingRow database,
            CliSnapshot runtime) {
        if (runtime != null
                && (!database.cliKey().equals(runtime.cliKey())
                    || !database.connectionKey().equals(runtime.connectionKey()))) {
            throw new IllegalStateException("runtime CLI metadata disagrees with database");
        }
        return new CliSnapshot(
            database.cliKey(),
            database.connectionKey(),
            database.offeringKey(),
            runtime == null ? "ERROR" : runtime.runtimeStatus());
    }

    private static OfferingAccessMetadata metadata(EnabledOfferingRow offering) {
        return new OfferingAccessMetadata(
            offering.offeringKey(),
            "PLATFORM",
            offering.connectionKey(),
            offering.kind(),
            offering.protocol(),
            offering.cliKey(),
            offering.modelKey(),
            offering.defaultAccess());
    }

    private static boolean isCli(EnabledOfferingRow offering) {
        return "CLI".equals(offering.kind()) && offering.cliKey() != null;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static AdminException invalidRequest() {
        return new AdminException(AdminErrorCode.INVALID_REQUEST);
    }

    private record AccessKey(Long userId, String offeringKey) {
    }
}
