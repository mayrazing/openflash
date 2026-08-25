package openflash_admin.service.impl;

import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.CliAdminSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.LoginSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.dto.AdminCodexResponse;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.CatalogRow;
import openflash_admin.service.AdminCodexService;
import org.springframework.stereotype.Service;

/** 由平台 catalog 读取 Codex 开关, 由 ai_runtime 提供实时状态和全部写操作. */
@Service
public class AdminCodexServiceImpl implements AdminCodexService {

    static final int GLOBAL_CHANGE_MAX_DELAY_SECONDS = 60;
    private static final String CLI_KEY = "codex";

    private final AdminPlatformAiMapper platformMapper;
    private final AiRuntimeAdminClient runtimeClient;

    public AdminCodexServiceImpl(
            AdminPlatformAiMapper platformMapper,
            AiRuntimeAdminClient runtimeClient) {
        this.platformMapper = platformMapper;
        this.runtimeClient = runtimeClient;
    }

    @Override
    public AdminCodexResponse snapshot() {
        CatalogRow database = platformMapper.findCliOffering(CLI_KEY);
        boolean enabled = database != null
            && database.connectionEnabled()
            && Boolean.TRUE.equals(database.offeringEnabled());
        try {
            CliAdminSnapshot runtime = runtimeClient.codexSnapshot();
            requireMatchingRuntime(database, runtime);
            return new AdminCodexResponse(
                enabled,
                runtime.cli().runtimeStatus(),
                runtime.login(),
                GLOBAL_CHANGE_MAX_DELAY_SECONDS);
        } catch (AdminRuntimeUnavailableException offline) {
            return new AdminCodexResponse(
                enabled,
                "ERROR",
                new LoginSnapshot("FAILED", null, null),
                GLOBAL_CHANGE_MAX_DELAY_SECONDS);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        CatalogRow database = platformMapper.findCliOffering(CLI_KEY);
        if (database == null) {
            throw new AdminException(AdminErrorCode.PLATFORM_AI_NOT_FOUND);
        }
        requireCodexRow(database);
        runtimeClient.updateConnection(
            database.connectionKey(),
            new UpdateConnectionRequest(
                database.baseUrl(), enabled, database.connectionSortOrder()));
    }

    @Override
    public LoginSnapshot startLogin() {
        return runtimeClient.startCodexLogin();
    }

    @Override
    public LoginSnapshot cancelLogin() {
        return runtimeClient.cancelCodexLogin();
    }

    @Override
    public void logoutAccount() {
        runtimeClient.logoutCodexAccount();
    }

    private static void requireMatchingRuntime(
            CatalogRow database,
            CliAdminSnapshot runtime) {
        if (runtime == null
                || runtime.cli() == null
                || !CLI_KEY.equals(runtime.cli().cliKey())) {
            throw new IllegalStateException("invalid Codex runtime snapshot");
        }
        if (database != null
                && (!database.connectionKey().equals(runtime.cli().connectionKey())
                    || !database.offeringKey().equals(runtime.cli().offeringKey()))) {
            throw new IllegalStateException("Codex runtime disagrees with database catalog");
        }
    }

    private static void requireCodexRow(CatalogRow row) {
        if (!"CLI".equals(row.kind())
                || !"CODEX_APP_SERVER".equals(row.protocol())
                || !CLI_KEY.equals(row.cliKey())
                || row.offeringId() == null
                || row.offeringKey() == null
                || row.offeringEnabled() == null) {
            throw new IllegalStateException("invalid Codex database catalog row");
        }
    }

}
