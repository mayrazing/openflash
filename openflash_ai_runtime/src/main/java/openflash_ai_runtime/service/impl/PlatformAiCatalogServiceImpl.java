package openflash_ai_runtime.service.impl;

import java.util.List;
import java.util.UUID;
import openflash_ai_runtime.entity.PlatformAiConnection;
import openflash_ai_runtime.entity.PlatformAiOffering;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper;
import openflash_ai_runtime.mapper.PlatformAiOfferingMapper.UsableOfferingRow;
import openflash_ai_runtime.mapper.PlatformAiUserAccessMapper;
import openflash_ai_runtime.common.CodexAppException;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformSecretService;
import openflash_ai_runtime.transport.PlatformAiTransport;
import openflash_ai_runtime.transport.PlatformAiTransportRegistry;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以 runtime 数据库查询为权威, 编排平台连接、授权、模型和生成. */
@Service
public class PlatformAiCatalogServiceImpl implements PlatformAiCatalogService {

    private static final String CODEX_CONNECTION_KEY = "platform-codex";
    private static final String CODEX_OFFERING_KEY = "platform-codex-cli";
    private static final String CODEX_CLI_KEY = "codex";
    private static final String CODEX_PROTOCOL = "CODEX_APP_SERVER";

    private final PlatformAiConnectionMapper connections;
    private final PlatformAiOfferingMapper offerings;
    private final PlatformAiUserAccessMapper access;
    private final PlatformSecretService secrets;
    private final PlatformAiTransportRegistry transports;
    private final CodexRuntimeService codex;
    private final PlatformGenerationRequestRegistry requestRegistry;

    @Autowired
    public PlatformAiCatalogServiceImpl(
            ObjectProvider<PlatformAiConnectionMapper> connectionProvider,
            ObjectProvider<PlatformAiOfferingMapper> offeringProvider,
            ObjectProvider<PlatformAiUserAccessMapper> accessProvider,
            ObjectProvider<PlatformSecretService> secretProvider,
            ObjectProvider<PlatformAiTransportRegistry> transportProvider,
            ObjectProvider<CodexRuntimeService> codexProvider,
            PlatformGenerationRequestRegistry requestRegistry) {
        this(
                connectionProvider.getIfAvailable(),
                offeringProvider.getIfAvailable(),
                accessProvider.getIfAvailable(),
                secretProvider.getIfAvailable(),
                transportProvider.getIfAvailable(),
                codexProvider.getIfAvailable(),
                requestRegistry);
    }

    PlatformAiCatalogServiceImpl(
            PlatformAiConnectionMapper connections,
            PlatformAiOfferingMapper offerings,
            PlatformAiUserAccessMapper access,
            PlatformSecretService secrets,
            PlatformAiTransportRegistry transports,
            CodexRuntimeService codex) {
        this(connections, offerings, access, secrets, transports, codex,
                new PlatformGenerationRequestRegistry());
    }

    PlatformAiCatalogServiceImpl(
            PlatformAiConnectionMapper connections,
            PlatformAiOfferingMapper offerings,
            PlatformAiUserAccessMapper access,
            PlatformSecretService secrets,
            PlatformAiTransportRegistry transports,
            CodexRuntimeService codex,
            PlatformGenerationRequestRegistry requestRegistry) {
        this.connections = connections;
        this.offerings = offerings;
        this.access = access;
        this.secrets = secrets;
        this.transports = transports;
        this.codex = codex;
        this.requestRegistry = requestRegistry;
    }

    public PageView page() {
        if (connections == null || offerings == null) {
            return new PageView("UNAVAILABLE", List.of());
        }
        List<ConnectionView> views = connections.findAll().stream()
                .map(this::connectionView)
                .toList();
        String status = views.stream().anyMatch(view -> "AVAILABLE".equals(
                connectionRuntimeStatus(view))) ? "AVAILABLE" : "UNAVAILABLE";
        return new PageView(status, views);
    }

    /** 创建 connection; 注册 CLI 的唯一动态 offering 在同一事务内创建. */
    @Transactional
    public ConnectionView createConnection(CreateConnectionCommand command) {
        requireCatalogMappers();
        if (command == null) throw invalidRequest();
        boolean codexCommand = "CLI".equals(command.kind())
                && CODEX_PROTOCOL.equals(command.protocol())
                && CODEX_CLI_KEY.equals(command.cliKey())
                && isBlank(command.baseUrl());
        boolean apiCommand = "API".equals(command.kind())
                && "ANTHROPIC".equals(command.protocol())
                && command.cliKey() == null
                && !isBlank(command.displayName())
                && !isBlank(command.baseUrl());
        if (!codexCommand && !apiCommand) throw invalidRequest();

        String connectionKey = codexCommand
                ? CODEX_CONNECTION_KEY
                : "platform-api-" + compactUuid();
        PlatformAiConnection connection = new PlatformAiConnection(
                0L, connectionKey, command.kind(), command.protocol(), command.cliKey(),
                blankToNull(command.displayName()), blankToNull(command.baseUrl()), false,
                true, command.sortOrder());
        try {
            connections.insert(connection);
            List<OfferingView> createdOfferings = List.of();
            if (codexCommand) {
                PlatformAiOffering offering = new PlatformAiOffering(
                        0L, connection.id(), CODEX_OFFERING_KEY, null,
                        true, false, 0);
                offerings.insert(offering);
                createdOfferings = List.of(offeringView(offering, connection));
            }
            return connectionView(connection, createdOfferings);
        } catch (DuplicateKeyException failure) {
            throw invalidRequest();
        }
    }

    /** Codex enabled 同步 connection 与动态 offering. */
    @Transactional
    public ConnectionView updateConnection(
            String connectionKey,
            UpdateConnectionCommand command) {
        requireCatalogMappers();
        if (command == null) throw invalidRequest();
        PlatformAiConnection existing = requireConnection(connectionKey);
        String baseUrl;
        if ("CLI".equals(existing.kind())) {
            if (!isCodex(existing)) throw invalidRequest();
            if (!isBlank(command.baseUrl())) throw invalidRequest();
            baseUrl = null;
        } else {
            if (!isSupportedApi(existing) || isBlank(command.baseUrl())) {
                throw invalidRequest();
            }
            baseUrl = command.baseUrl().trim();
        }
        connections.update(connectionKey, baseUrl, command.enabled(), command.sortOrder());
        if (isCodex(existing)) {
            offerings.updateEnabledByConnectionId(existing.id(), command.enabled());
        }
        PlatformAiConnection updated = new PlatformAiConnection(
                existing.id(), existing.connectionKey(), existing.kind(), existing.protocol(),
                existing.cliKey(), existing.displayName(), baseUrl, existing.credentialsConfigured(),
                command.enabled(), command.sortOrder());
        List<OfferingView> children = offerings.findByConnectionId(existing.id()).stream()
                .map(offering -> offeringView(offering, updated))
                .toList();
        return connectionView(updated, children);
    }

    /** 永久删除连接; 外键级联 offering/access/preference/selection. */
    @Transactional
    public void deleteConnection(String connectionKey) {
        requireCatalogMappers();
        requireConnection(connectionKey);
        connections.deleteByKey(connectionKey);
    }

    public void replaceCredentials(String connectionKey, String apiKey) {
        if (secrets == null) throw unavailable();
        PlatformAiConnection connection = requireConnection(connectionKey);
        if (!isSupportedApi(connection)) throw invalidRequest();
        secrets.replace(connectionKey, apiKey);
    }

    /** 每次发现重新加载 connection 和密钥, 再由 protocol transport 校验目标. */
    public List<String> discoverModels(String connectionKey) {
        PlatformAiConnection connection = requireConnection(connectionKey);
        if (!connection.enabled()) throw unavailable();
        if ("CLI".equals(connection.kind())) {
            if (!isCodex(connection)) throw invalidRequest();
            return codexCatalog().models().stream().map(CodexModelCatalog.Model::model).toList();
        }
        if (!isSupportedApi(connection)) throw invalidRequest();
        if (secrets == null || transports == null) throw unavailable();
        String apiKey = secrets.requirePlaintext(connection.id());
        return transports.require(connection.protocol()).discoverModels(
                new PlatformAiTransport.ConnectionTarget(connection.baseUrl(), apiKey));
    }

    /** 使用尚未保存的 API 配置发现模型, 不持久化密钥. */
    public List<String> discoverModels(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey) || transports == null) throw invalidRequest();
        return transports.require("ANTHROPIC").discoverModels(
                new PlatformAiTransport.ConnectionTarget(baseUrl.trim(), apiKey));
    }

    @Transactional
    public OfferingView createOffering(
            String connectionKey,
            CreateOfferingCommand command) {
        requireCatalogMappers();
        if (command == null || isBlank(command.modelKey())) throw invalidRequest();
        PlatformAiConnection connection = requireConnection(connectionKey);
        if (!isSupportedApi(connection)) throw invalidRequest();
        PlatformAiOffering offering = new PlatformAiOffering(
                0L, connection.id(), "platform-api-offering-" + compactUuid(),
                command.modelKey().trim(), true, false, command.sortOrder());
        offerings.insert(offering);
        return offeringView(offering, connection);
    }

    /** API offering 独立启停; CLI 更新仍同步唯一总开关. */
    @Transactional
    public OfferingView updateOffering(
            String offeringKey,
            UpdateOfferingCommand command) {
        requireCatalogMappers();
        if (command == null) throw invalidRequest();
        PlatformAiOffering existing = requireOffering(offeringKey);
        PlatformAiConnection connection = requireConnectionById(existing.connectionId());
        String modelKey;
        if ("CLI".equals(connection.kind())) {
            if (!isCodex(connection)) throw invalidRequest();
            if (command.modelKey() != null) throw invalidRequest();
            modelKey = null;
            connections.update(connection.connectionKey(), null,
                    command.enabled(), connection.sortOrder());
        } else {
            if (isBlank(command.modelKey())) throw invalidRequest();
            modelKey = command.modelKey().trim();
        }
        offerings.update(offeringKey, modelKey, command.enabled(), command.sortOrder());
        PlatformAiOffering updated = new PlatformAiOffering(
                existing.id(), existing.connectionId(), existing.offeringKey(), modelKey,
                command.enabled(), existing.defaultAccess(), command.sortOrder());
        return offeringView(updated, connection);
    }

    @Transactional
    public void deleteOffering(String offeringKey) {
        requireCatalogMappers();
        PlatformAiOffering offering = requireOffering(offeringKey);
        PlatformAiConnection connection = requireConnectionById(offering.connectionId());
        if ("CLI".equals(connection.kind())) throw invalidRequest();
        offerings.deleteByKey(offeringKey);
    }

    @Transactional
    public void setDefaultAccess(String offeringKey, boolean enabled) {
        requireCatalogMappers();
        requireOffering(offeringKey);
        offerings.updateDefaultAccess(offeringKey, enabled);
    }

    @Transactional
    public void setUserAccess(String offeringKey, long userId, boolean enabled) {
        requireCatalogMappers();
        if (userId <= 0L) throw invalidRequest();
        PlatformAiOffering offering = requireOffering(offeringKey);
        access.upsert(userId, offering.id(), enabled);
    }

    @Transactional
    public void deleteUserAccess(String offeringKey, long userId) {
        requireCatalogMappers();
        if (userId <= 0L) throw invalidRequest();
        PlatformAiOffering offering = requireOffering(offeringKey);
        access.delete(userId, offering.id());
    }

    /** 每次列表直接执行 usable SQL, 不接受调用方权限结论. */
    public List<OfferingView> listUsableOfferings(long userId) {
        if (userId <= 0L) throw invalidRequest();
        if (offerings == null) throw unavailable();
        return offerings.findUsableByUserId(userId).stream()
                .filter(PlatformAiCatalogServiceImpl::isSupported)
                .map(this::usableView)
                .toList();
    }

    /** 每次模型读取重新加载 usable row; CLI 只返回实时 catalog. */
    public ModelsView models(long userId, String offeringKey) {
        UsableOfferingRow row = requireUsable(userId, offeringKey);
        if (!isSupported(row)) throw invalidRequest();
        if ("CLI".equals(row.kind())) return codexModelsView();
        if (secrets == null) throw unavailable();
        secrets.requirePlaintext(row.connectionId());
        ModelView model = new ModelView(
                row.modelKey(), row.modelKey(), "", true, null, List.of());
        return new ModelsView("AVAILABLE", List.of(model));
    }

    /** 每次生成重新加载 usable row并校验固定或实时模型. */
    public String generate(GenerationCommand command) {
        if (command == null) throw invalidRequest();
        GenerationRequestValidator.validatePlatform(
                command.requestId(), command.userId(), command.offeringKey(), command.model(),
                command.reasoningEffort(), command.prompt(), command.systemPrompt(),
                command.temperature());
        RequestState requestState = requestRegistry.reserve(command.requestId());
        try {
            UsableOfferingRow row = requireUsable(command.userId(), command.offeringKey());
            if (requestState.isCancelled()) throw unavailable();
            if (!isSupported(row)) throw invalidRequest();
            if ("API".equals(row.kind())) {
                if (secrets == null || transports == null) throw unavailable();
                if (!command.model().equals(row.modelKey())) throw invalidRequest();
                String apiKey = secrets.requirePlaintext(row.connectionId());
                if (requestState.isCancelled()) throw unavailable();
                return transports.require(row.protocol()).generate(
                        new PlatformAiTransport.GenerateCommand(
                                command.requestId(), row.baseUrl(), apiKey, command.model(),
                                command.prompt(), command.systemPrompt(), command.temperature()),
                        requestState);
            }
            if (!CODEX_PROTOCOL.equals(row.protocol()) || !CODEX_CLI_KEY.equals(row.cliKey())) {
                throw invalidRequest();
            }
            CodexModelCatalog.Selection selection;
            try {
                selection = codexCatalog().validate(command.model(), command.reasoningEffort());
            } catch (IllegalArgumentException failure) {
                throw invalidRequest();
            }
            if (requestState.isCancelled()) throw unavailable();
            try {
                return codex.generate(
                        command.requestId(),
                        command.prompt(),
                        new GenerationProfile(
                                selection.model(), command.systemPrompt(), command.temperature(),
                                selection.reasoningEffort()),
                        requestState);
            } catch (CodexAppException failure) {
                throw unavailable();
            }
        } finally {
            requestRegistry.complete(requestState);
        }
    }

    public boolean cancel(UUID requestId) {
        if (requestId == null) throw invalidRequest();
        if (requestRegistry.cancel(requestId)) return true;
        boolean codexCancelled = codex != null && codex.cancel(requestId);
        return (transports != null && transports.cancel(requestId)) || codexCancelled;
    }

    private ConnectionView connectionView(PlatformAiConnection connection) {
        List<OfferingView> children = offerings.findByConnectionId(connection.id()).stream()
                .map(offering -> offeringView(offering, connection))
                .toList();
        return connectionView(connection, children);
    }

    private ConnectionView connectionView(
            PlatformAiConnection connection,
            List<OfferingView> children) {
        return new ConnectionView(
                connection.connectionKey(), connection.kind(), connection.protocol(),
                connection.displayName(), connection.baseUrl(), connection.credentialsConfigured(), connection.enabled(),
                connection.sortOrder(), children);
    }

    private OfferingView offeringView(
            PlatformAiOffering offering,
            PlatformAiConnection connection) {
        return new OfferingView(
                offering.offeringKey(), offering.modelKey(), offering.enabled(),
                offering.defaultAccess(), offering.sortOrder(),
                runtimeStatus(connection), connection.kind(), connection.protocol());
    }

    private OfferingView usableView(UsableOfferingRow row) {
        String status = "CLI".equals(row.kind())
                ? codexRuntimeStatus()
                : row.credentialsConfigured() ? "AVAILABLE" : "UNAVAILABLE";
        return new OfferingView(
                row.offeringKey(), row.modelKey(), true, row.defaultAccess(),
                row.offeringSortOrder(), status, row.kind(), row.protocol());
    }

    private String runtimeStatus(PlatformAiConnection connection) {
        if (!connection.enabled()) return "UNAVAILABLE";
        if (isCodex(connection)) return codexRuntimeStatus();
        return connection.credentialsConfigured() ? "AVAILABLE" : "UNAVAILABLE";
    }

    private String connectionRuntimeStatus(ConnectionView connection) {
        if (!connection.enabled()) return "UNAVAILABLE";
        if ("CLI".equals(connection.kind())) return codexRuntimeStatus();
        return connection.credentialsConfigured() ? "AVAILABLE" : "UNAVAILABLE";
    }

    private String codexRuntimeStatus() {
        if (codex == null) return "UNAVAILABLE";
        try {
            return codex.status().status().name();
        } catch (RuntimeException failure) {
            return "UNAVAILABLE";
        }
    }

    private ModelsView codexModelsView() {
        CodexModelCatalog.Catalog catalog = codexCatalog();
        return new ModelsView("AVAILABLE", catalog.models().stream().map(model ->
                new ModelView(
                        model.model(), model.displayName(), model.description(),
                        model.defaultModel(), model.defaultReasoningEffort(),
                        model.supportedReasoningEfforts().stream().map(effort ->
                                new ReasoningEffortView(
                                        effort.reasoningEffort(), effort.description()))
                                .toList()))
                .toList());
    }

    private CodexModelCatalog.Catalog codexCatalog() {
        if (codex == null) throw unavailable();
        try {
            CodexModelCatalog.Catalog catalog = codex.models().toCompletableFuture().join();
            if (catalog == null) throw unavailable();
            return catalog;
        } catch (openflash_ai_runtime.common.RuntimeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private PlatformAiConnection requireConnection(String connectionKey) {
        if (connections == null) throw unavailable();
        if (isBlank(connectionKey)) throw invalidRequest();
        PlatformAiConnection connection = connections.findByKey(connectionKey);
        if (connection == null) throw notFound();
        return connection;
    }

    private PlatformAiConnection requireConnectionById(long id) {
        if (connections == null) throw unavailable();
        PlatformAiConnection connection = connections.findById(id);
        if (connection == null) throw notFound();
        return connection;
    }

    private PlatformAiOffering requireOffering(String offeringKey) {
        if (offerings == null) throw unavailable();
        if (isBlank(offeringKey)) throw invalidRequest();
        PlatformAiOffering offering = offerings.findByKey(offeringKey);
        if (offering == null) throw notFound();
        return offering;
    }

    private UsableOfferingRow requireUsable(long userId, String offeringKey) {
        if (userId <= 0L || isBlank(offeringKey)) throw invalidRequest();
        if (offerings == null) throw unavailable();
        UsableOfferingRow row = offerings.findUsableByKeyAndUserId(offeringKey, userId);
        if (row == null) throw notFound();
        return row;
    }

    private static boolean isCodex(PlatformAiConnection connection) {
        return CODEX_CONNECTION_KEY.equals(connection.connectionKey())
                && "CLI".equals(connection.kind())
                && CODEX_PROTOCOL.equals(connection.protocol())
                && CODEX_CLI_KEY.equals(connection.cliKey());
    }

    private static boolean isSupportedApi(PlatformAiConnection connection) {
        return "API".equals(connection.kind())
                && "ANTHROPIC".equals(connection.protocol())
                && connection.cliKey() == null;
    }

    private static boolean isSupported(UsableOfferingRow row) {
        if ("API".equals(row.kind())) {
            return "ANTHROPIC".equals(row.protocol())
                    && row.cliKey() == null
                    && !isBlank(row.baseUrl())
                    && !isBlank(row.modelKey());
        }
        return "CLI".equals(row.kind())
                && CODEX_CONNECTION_KEY.equals(row.connectionKey())
                && CODEX_PROTOCOL.equals(row.protocol())
                && CODEX_CLI_KEY.equals(row.cliKey())
                && CODEX_OFFERING_KEY.equals(row.offeringKey())
                && row.modelKey() == null;
    }

    private void requireCatalogMappers() {
        if (connections == null || offerings == null || access == null) {
            throw unavailable();
        }
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    private static openflash_ai_runtime.common.RuntimeException notFound() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.NOT_FOUND);
    }

    private static openflash_ai_runtime.common.RuntimeException unavailable() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
    }

}
