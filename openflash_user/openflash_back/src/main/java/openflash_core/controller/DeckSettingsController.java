package openflash_core.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.DeckSettings;
import openflash_core.dto.DeckSettingsUpdateCommand;
import openflash_core.entity.PracticeModeOption;
import openflash_core.service.DeckSettingsService;
import openflash_core.service.TypeRegistryService;

@RestController
public class DeckSettingsController {

    private final DeckSettingsService deckSettingsService;
    private final TypeRegistryService typeRegistryService;

    public DeckSettingsController(DeckSettingsService deckSettingsService, TypeRegistryService typeRegistryService) {
        this.deckSettingsService = deckSettingsService;
        this.typeRegistryService = typeRegistryService;
    }

    @GetMapping("/api/decks/{deckId}/settings")
    public ApiResponse<DeckSettingsResponse> getSettings(@PathVariable Long deckId) {
        return ApiResponse.success(DeckSettingsResponse.from(deckSettingsService.getSettings(deckId)));
    }

    @PutMapping("/api/decks/{deckId}/settings")
    public ApiResponse<DeckSettingsResponse> updateSettings(
        @PathVariable Long deckId,
        @RequestBody DeckSettingsRequest request
    ) {
        return ApiResponse.success(
            DeckSettingsResponse.from(deckSettingsService.updateSettings(deckId, request.toCommand()))
        );
    }

    /**
     * 读取卡包设置页可选择的学习强度档位。
     */
    @GetMapping("/api/deck-settings/review-load-profiles")
    public ApiResponse<List<PracticeModeOption>> listReviewLoadProfiles() {
        return ApiResponse.success(typeRegistryService.getEnabledReviewLoadProfiles());
    }

    public record DeckSettingsRequest(
        Integer newCardsPerDay,
        BigDecimal targetRetention,
        String reviewLoadProfile,
        Boolean duplicateSideAEnabled,
        Boolean duplicateSideBEnabled
    ) {
        /**
         * 把页面保存内容转成服务层命令，后续字段增删只按名字映射。
         */
        DeckSettingsUpdateCommand toCommand() {
            return new DeckSettingsUpdateCommand(
                newCardsPerDay,
                targetRetention,
                reviewLoadProfile,
                duplicateSideAEnabled,
                duplicateSideBEnabled
            );
        }
    }

    public record DeckSettingsResponse(
        Long deckId,
        Integer newCardsPerDay,
        BigDecimal targetRetention,
        String reviewLoadProfile,
        Boolean duplicateSideAEnabled,
        Boolean duplicateSideBEnabled
    ) {
        static DeckSettingsResponse from(DeckSettings settings) {
            return new DeckSettingsResponse(
                settings.getDeckId(),
                settings.getNewCardsPerDay(),
                settings.getTargetRetention(),
                settings.getReviewLoadProfile(),
                settings.getDuplicateSideAEnabled(),
                settings.getDuplicateSideBEnabled()
            );
        }
    }
}
