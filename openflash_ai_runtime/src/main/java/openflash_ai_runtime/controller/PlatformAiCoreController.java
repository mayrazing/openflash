package openflash_ai_runtime.controller;

import java.util.List;
import java.util.UUID;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.service.PlatformAiCatalogService.ModelView;
import openflash_ai_runtime.service.PlatformAiCatalogService.OfferingView;
import openflash_ai_runtime.service.PlatformAiCatalogService.ReasoningEffortView;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** core 作用域的平台 offering、实时模型与生成 API. */
@RestController
@RequestMapping("/api/internal/core/platform-ai")
public class PlatformAiCoreController {

    private final PlatformAiCatalogService service;

    public PlatformAiCoreController(PlatformAiCatalogService service) {
        this.service = service;
    }

    @GetMapping("/offerings")
    public List<OfferingSnapshot> offerings(@RequestParam long userId) {
        requireUser(userId);
        return service.listUsableOfferings(userId).stream().map(this::offering).toList();
    }

    @GetMapping("/offerings/{offeringKey}/models")
    public ModelsSnapshot models(
            @PathVariable String offeringKey,
            @RequestParam long userId) {
        requireUser(userId);
        var models = service.models(userId, offeringKey);
        return new ModelsSnapshot(
                models.runtimeStatus(), models.models().stream().map(this::model).toList());
    }

    @PostMapping("/generations")
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        if (request == null) throw invalidRequest();
        GenerationRequestValidator.validatePlatform(
                request.requestId(), request.userId(), request.offeringKey(), request.model(),
                request.reasoningEffort(), request.prompt(), request.systemPrompt(),
                request.temperature());
        return new GenerateResponse(service.generate(
                new PlatformAiCatalogService.GenerationCommand(
                        request.requestId(), request.userId(), request.offeringKey(),
                        request.model(), request.reasoningEffort(), request.prompt(),
                        request.systemPrompt(), request.temperature())));
    }

    @DeleteMapping("/generations/{requestId}")
    public CancelResponse cancel(@PathVariable UUID requestId) {
        return new CancelResponse(service.cancel(requestId));
    }

    private OfferingSnapshot offering(OfferingView source) {
        return new OfferingSnapshot(
                source.offeringKey(), "PLATFORM", source.kind(), source.protocol(), source.modelKey(),
                source.runtimeStatus(), true, source.enabled());
    }

    private ModelSnapshot model(ModelView source) {
        return new ModelSnapshot(
                source.model(), source.displayName(), source.description(),
                source.defaultModel(), source.defaultReasoningEffort(),
                source.supportedReasoningEfforts().stream().map(this::effort).toList());
    }

    private ReasoningEffortSnapshot effort(ReasoningEffortView source) {
        return new ReasoningEffortSnapshot(source.reasoningEffort(), source.description());
    }

    private static void requireUser(long userId) {
        if (userId <= 0L) throw invalidRequest();
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    public record OfferingSnapshot(
            String offeringKey,
            String source,
            String kind,
            String protocol,
            String modelKey,
            String runtimeStatus,
            boolean accessGranted,
            boolean enabled) {
    }

    public record ModelsSnapshot(String runtimeStatus, List<ModelSnapshot> models) {
    }

    public record ModelSnapshot(
            String model,
            String displayName,
            String description,
            boolean defaultModel,
            String defaultReasoningEffort,
            List<ReasoningEffortSnapshot> supportedReasoningEfforts) {
    }

    public record ReasoningEffortSnapshot(String reasoningEffort, String description) {
    }

    public record GenerateRequest(
            UUID requestId,
            long userId,
            String offeringKey,
            String model,
            String reasoningEffort,
            String prompt,
            String systemPrompt,
            Double temperature) {

        @Override
        public String toString() {
            return "GenerateRequest[requestId=" + requestId
                    + ", userId=" + userId
                    + ", offeringKey=" + offeringKey
                    + ", model=" + model
                    + ", reasoningEffort=" + reasoningEffort
                    + ", prompt=<redacted>, systemPrompt=<redacted>, temperature="
                    + temperature + "]";
        }
    }

    public record GenerateResponse(String content) {
    }

    public record CancelResponse(boolean cancelled) {
    }
}
