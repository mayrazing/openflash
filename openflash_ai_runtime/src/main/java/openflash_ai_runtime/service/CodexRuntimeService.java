package openflash_ai_runtime.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import openflash_ai_runtime.client.CodexAppServerClient;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.support.CodexLoginCoordinator;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;

public interface CodexRuntimeService {

    CodexAppServerClient.StatusResponse status();

    CompletionStage<CodexModelCatalog.Catalog> models();

    String generate(UUID requestId, String prompt, GenerationProfile profile);

    String generate(
            UUID requestId,
            String prompt,
            GenerationProfile profile,
            RequestState requestState);

    boolean cancel(UUID requestId);

    CompletionStage<CodexLoginCoordinator.LoginSnapshot> startLogin();

    CompletionStage<CodexLoginCoordinator.LoginSnapshot> cancelLogin();

    CodexLoginCoordinator.LoginSnapshot loginSnapshot();

    CompletionStage<Boolean> logoutAccount();

    void shutdown();
}
