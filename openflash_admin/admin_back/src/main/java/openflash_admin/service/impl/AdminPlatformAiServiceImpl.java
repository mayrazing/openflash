package openflash_admin.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openflash_admin.client.AiRuntimeAdminClient;
import openflash_admin.client.AiRuntimeAdminClient.AdminRuntimeUnavailableException;
import openflash_admin.client.AiRuntimeAdminClient.ConnectionSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.CreateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.CreateOfferingRequest;
import openflash_admin.client.AiRuntimeAdminClient.DiscoveredModel;
import openflash_admin.client.AiRuntimeAdminClient.DiscoverModelsRequest;
import openflash_admin.client.AiRuntimeAdminClient.OfferingSnapshot;
import openflash_admin.client.AiRuntimeAdminClient.ReplaceCredentialsRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetDefaultAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.SetUserAccessRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateConnectionRequest;
import openflash_admin.client.AiRuntimeAdminClient.UpdateOfferingRequest;
import openflash_admin.dto.ConnectionResponse;
import openflash_admin.dto.OfferingResponse;
import openflash_admin.dto.PlatformAiPageResponse;
import openflash_admin.mapper.AdminPlatformAiMapper;
import openflash_admin.mapper.AdminPlatformAiMapper.CatalogRow;
import openflash_admin.service.AdminPlatformAiService;
import org.springframework.stereotype.Service;

/** 用数据库安全 metadata 组成管理页, runtime 只补充实时状态并执行写入. */
@Service
public class AdminPlatformAiServiceImpl implements AdminPlatformAiService {

    private final AdminPlatformAiMapper mapper;
    private final AiRuntimeAdminClient runtimeClient;

    public AdminPlatformAiServiceImpl(
            AdminPlatformAiMapper mapper,
            AiRuntimeAdminClient runtimeClient) {
        this.mapper = mapper;
        this.runtimeClient = runtimeClient;
    }

    @Override
    public PlatformAiPageResponse page() {
        List<DatabaseConnection> database = databaseConnections(mapper.findCatalogRows());
        try {
            var runtime = runtimeClient.platformAiPage();
            Map<String, ConnectionSnapshot> runtimeByKey = new LinkedHashMap<>();
            for (ConnectionSnapshot connection : runtime.connections()) {
                if (runtimeByKey.put(connection.connectionKey(), connection) != null) {
                    throw new IllegalStateException("duplicate runtime connection key");
                }
            }
            return new PlatformAiPageResponse(
                runtime.runtimeStatus(),
                true,
                database.stream()
                    .map(connection -> online(connection, runtimeByKey.get(connection.connectionKey)))
                    .toList());
        } catch (AdminRuntimeUnavailableException offline) {
            return new PlatformAiPageResponse(
                "ERROR",
                false,
                database.stream().map(this::offline).toList());
        }
    }

    @Override
    public ConnectionSnapshot createConnection(CreateConnectionRequest request) {
        return runtimeClient.createConnection(request);
    }

    @Override
    public ConnectionSnapshot updateConnection(
            String connectionKey,
            UpdateConnectionRequest request) {
        return runtimeClient.updateConnection(connectionKey, request);
    }

    @Override
    public void replaceCredentials(
            String connectionKey,
            ReplaceCredentialsRequest request) {
        runtimeClient.replaceCredentials(connectionKey, request);
    }

    @Override
    public void deleteConnection(String connectionKey) {
        runtimeClient.deleteConnection(connectionKey);
    }

    @Override
    public List<DiscoveredModel> discoverModels(String connectionKey) {
        return runtimeClient.discoverModels(connectionKey);
    }

    @Override
    public List<DiscoveredModel> discoverModels(DiscoverModelsRequest request) {
        return runtimeClient.discoverModels(request);
    }

    @Override
    public OfferingSnapshot createOffering(
            String connectionKey,
            CreateOfferingRequest request) {
        return runtimeClient.createOffering(connectionKey, request);
    }

    @Override
    public OfferingSnapshot updateOffering(
            String offeringKey,
            UpdateOfferingRequest request) {
        return runtimeClient.updateOffering(offeringKey, request);
    }

    @Override
    public void deleteOffering(String offeringKey) {
        runtimeClient.deleteOffering(offeringKey);
    }

    @Override
    public void setDefaultAccess(
            String offeringKey,
            SetDefaultAccessRequest request) {
        runtimeClient.setDefaultAccess(offeringKey, request);
    }

    @Override
    public void setUserAccess(
            String offeringKey,
            long userId,
            SetUserAccessRequest request) {
        runtimeClient.setUserAccess(offeringKey, userId, request);
    }

    @Override
    public void deleteUserAccess(String offeringKey, long userId) {
        runtimeClient.deleteUserAccess(offeringKey, userId);
    }

    private List<DatabaseConnection> databaseConnections(List<CatalogRow> rows) {
        Map<Long, DatabaseConnectionBuilder> grouped = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            DatabaseConnectionBuilder connection = grouped.computeIfAbsent(
                row.connectionId(), ignored -> new DatabaseConnectionBuilder(row));
            connection.requireSameConnection(row);
            if (row.offeringId() != null) connection.addOffering(row);
        }
        return grouped.values().stream().map(DatabaseConnectionBuilder::build).toList();
    }

    private ConnectionResponse online(
            DatabaseConnection database,
            ConnectionSnapshot runtime) {
        Map<String, OfferingSnapshot> runtimeOfferings = new LinkedHashMap<>();
        if (runtime != null) {
            for (OfferingSnapshot offering : runtime.offerings()) {
                if (runtimeOfferings.put(offering.offeringKey(), offering) != null) {
                    throw new IllegalStateException("duplicate runtime offering key");
                }
            }
        }
        return new ConnectionResponse(
            database.connectionKey(),
            "PLATFORM",
            database.kind(),
            database.protocol(),
            database.displayName(),
            database.baseUrl(),
            runtime == null
                ? database.credentialsConfigured()
                : runtime.credentialsConfigured(),
            database.enabled(),
            database.sortOrder(),
            database.offerings().stream().map(offering -> {
                OfferingSnapshot live = runtimeOfferings.get(offering.offeringKey());
                return offering.response(live == null ? "UNAVAILABLE" : live.runtimeStatus());
            }).toList());
    }

    private ConnectionResponse offline(DatabaseConnection database) {
        return new ConnectionResponse(
            database.connectionKey(),
            "PLATFORM",
            database.kind(),
            database.protocol(),
            database.displayName(),
            database.baseUrl(),
            database.credentialsConfigured(),
            database.enabled(),
            database.sortOrder(),
            database.offerings().stream()
                .map(offering -> offering.response("ERROR"))
                .toList());
    }

    private record DatabaseConnection(
        long id,
        String connectionKey,
        String kind,
        String protocol,
        String displayName,
        String baseUrl,
        boolean credentialsConfigured,
        boolean enabled,
        int sortOrder,
        List<DatabaseOffering> offerings
    ) {
    }

    private record DatabaseOffering(
        String offeringKey,
        String modelKey,
        boolean enabled,
        boolean defaultAccess,
        int sortOrder
    ) {
        private OfferingResponse response(String runtimeStatus) {
            return new OfferingResponse(
                offeringKey, "PLATFORM", modelKey, enabled, defaultAccess,
                sortOrder, runtimeStatus);
        }
    }

    private static final class DatabaseConnectionBuilder {
        private final CatalogRow connection;
        private final List<DatabaseOffering> offerings = new ArrayList<>();

        private DatabaseConnectionBuilder(CatalogRow connection) {
            this.connection = connection;
        }

        private void requireSameConnection(CatalogRow row) {
            if (!connection.connectionKey().equals(row.connectionKey())
                    || !connection.kind().equals(row.kind())
                    || !connection.protocol().equals(row.protocol())) {
                throw new IllegalStateException("inconsistent platform connection rows");
            }
        }

        private void addOffering(CatalogRow row) {
            if (row.offeringKey() == null
                    || row.offeringEnabled() == null
                    || row.defaultAccess() == null
                    || row.offeringSortOrder() == null) {
                throw new IllegalStateException("incomplete platform offering row");
            }
            offerings.add(new DatabaseOffering(
                row.offeringKey(), row.modelKey(), row.offeringEnabled(),
                row.defaultAccess(), row.offeringSortOrder()));
        }

        private DatabaseConnection build() {
            return new DatabaseConnection(
                connection.connectionId(),
                connection.connectionKey(),
                connection.kind(),
                connection.protocol(),
                connection.displayName(),
                connection.baseUrl(),
                connection.credentialsConfigured(),
                connection.connectionEnabled(),
                connection.connectionSortOrder(),
                List.copyOf(offerings));
        }
    }
}
