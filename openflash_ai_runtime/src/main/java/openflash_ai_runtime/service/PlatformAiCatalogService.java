package openflash_ai_runtime.service;

import java.util.List;
import java.util.UUID;

public interface PlatformAiCatalogService {

    PageView page();

    ConnectionView createConnection(CreateConnectionCommand command);

    ConnectionView updateConnection(String connectionKey, UpdateConnectionCommand command);

    void deleteConnection(String connectionKey);

    void replaceCredentials(String connectionKey, String apiKey);

    List<String> discoverModels(String connectionKey);

    List<String> discoverModels(String baseUrl, String apiKey);

    OfferingView createOffering(String connectionKey, CreateOfferingCommand command);

    OfferingView updateOffering(String offeringKey, UpdateOfferingCommand command);

    void deleteOffering(String offeringKey);

    void setDefaultAccess(String offeringKey, boolean enabled);

    void setUserAccess(String offeringKey, long userId, boolean enabled);

    void deleteUserAccess(String offeringKey, long userId);

    List<OfferingView> listUsableOfferings(long userId);

    ModelsView models(long userId, String offeringKey);

    String generate(GenerationCommand command);

    boolean cancel(UUID requestId);

    record PageView(String runtimeStatus, List<ConnectionView> connections) {
        public PageView { connections = List.copyOf(connections); }
    }

    record ConnectionView(
            String connectionKey, String kind, String protocol, String displayName, String baseUrl,
            boolean credentialsConfigured, boolean enabled, int sortOrder,
            List<OfferingView> offerings) {
        public ConnectionView { offerings = List.copyOf(offerings); }

        public ConnectionView(
                String connectionKey, String kind, String protocol, String baseUrl,
                boolean credentialsConfigured, boolean enabled, int sortOrder,
                List<OfferingView> offerings) {
            this(connectionKey, kind, protocol, null, baseUrl, credentialsConfigured,
                    enabled, sortOrder, offerings);
        }
    }

    record OfferingView(
            String offeringKey, String modelKey, boolean enabled, boolean defaultAccess,
            int sortOrder, String runtimeStatus, String kind, String protocol) {
    }

    record ModelsView(String runtimeStatus, List<ModelView> models) {
        public ModelsView { models = List.copyOf(models); }
    }

    record ModelView(
            String model, String displayName, String description, boolean defaultModel,
            String defaultReasoningEffort,
            List<ReasoningEffortView> supportedReasoningEfforts) {
        public ModelView { supportedReasoningEfforts = List.copyOf(supportedReasoningEfforts); }
    }

    record ReasoningEffortView(String reasoningEffort, String description) {
    }

    record CreateConnectionCommand(
            String kind, String protocol, String cliKey, String displayName,
            String baseUrl, int sortOrder) {
        public CreateConnectionCommand(
                String kind, String protocol, String cliKey, String baseUrl, int sortOrder) {
            this(kind, protocol, cliKey, null, baseUrl, sortOrder);
        }
    }

    record UpdateConnectionCommand(String baseUrl, boolean enabled, int sortOrder) {
    }

    record CreateOfferingCommand(String modelKey, int sortOrder) {
    }

    record UpdateOfferingCommand(String modelKey, boolean enabled, int sortOrder) {
    }

    record GenerationCommand(
            UUID requestId, long userId, String offeringKey, String model,
            String reasoningEffort, String prompt, String systemPrompt, Double temperature) {

        @Override
        public String toString() {
            return "GenerationCommand[requestId=" + requestId
                    + ", userId=" + userId
                    + ", offeringKey=" + offeringKey
                    + ", model=" + model
                    + ", reasoningEffort=" + reasoningEffort
                    + ", prompt=<redacted>, systemPrompt=<redacted>, temperature="
                    + temperature + "]";
        }
    }
}
