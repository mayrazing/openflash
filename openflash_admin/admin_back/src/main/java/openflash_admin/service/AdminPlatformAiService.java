package openflash_admin.service;

import java.util.List;
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
import openflash_admin.dto.PlatformAiPageResponse;

public interface AdminPlatformAiService {

    PlatformAiPageResponse page();

    ConnectionSnapshot createConnection(CreateConnectionRequest request);

    ConnectionSnapshot updateConnection(String connectionKey, UpdateConnectionRequest request);

    void replaceCredentials(String connectionKey, ReplaceCredentialsRequest request);

    void deleteConnection(String connectionKey);

    List<DiscoveredModel> discoverModels(String connectionKey);

    List<DiscoveredModel> discoverModels(DiscoverModelsRequest request);

    OfferingSnapshot createOffering(String connectionKey, CreateOfferingRequest request);

    OfferingSnapshot updateOffering(String offeringKey, UpdateOfferingRequest request);

    void deleteOffering(String offeringKey);

    void setDefaultAccess(String offeringKey, SetDefaultAccessRequest request);

    void setUserAccess(String offeringKey, long userId, SetUserAccessRequest request);

    void deleteUserAccess(String offeringKey, long userId);
}
