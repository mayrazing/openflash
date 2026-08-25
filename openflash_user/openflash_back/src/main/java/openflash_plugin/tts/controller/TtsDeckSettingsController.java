package openflash_plugin.tts.controller;

import java.util.List;
import openflash_plugin.tts.dto.TtsDeckSettingsUpdateCommand;
import openflash_plugin.tts.entity.TtsDeckSettings;
import openflash_plugin.tts.service.TtsDeckSettingsService;
import openflash_plugin.tts.service.impl.TtsFeatureGuard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.dto.ApiResponse;

@RestController
public class TtsDeckSettingsController {

    private final TtsDeckSettingsService ttsDeckSettingsService;
    private final TtsFeatureGuard featureGuard;

    public TtsDeckSettingsController(
            TtsDeckSettingsService ttsDeckSettingsService,
            TtsFeatureGuard featureGuard) {
        this.ttsDeckSettingsService = ttsDeckSettingsService;
        this.featureGuard = featureGuard;
    }

    @GetMapping("/api/plugins/tts/decks/{deckId}/settings")
    public ApiResponse<TtsDeckSettings> getSettings(@PathVariable Long deckId) {
        featureGuard.ensureTtsEnabled();
        return ApiResponse.success(ttsDeckSettingsService.getForCurrentUser(deckId));
    }

    @PutMapping("/api/plugins/tts/decks/{deckId}/settings")
    public ApiResponse<TtsDeckSettings> updateSettings(
            @PathVariable Long deckId,
            @RequestBody(required = false) TtsDeckSettingsRequest request) {
        featureGuard.ensureTtsEnabled();
        if (request == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
        return ApiResponse.success(
            ttsDeckSettingsService.saveForCurrentUser(deckId, request.toCommand()));
    }

    @GetMapping("/api/plugins/tts/engines")
    public ApiResponse<List<String>> getEnabledEngines() {
        featureGuard.ensureTtsEnabled();
        return ApiResponse.success(ttsDeckSettingsService.getEnabledEngines());
    }

    public record TtsDeckSettingsRequest(Boolean autoSpeakA, Boolean autoSpeakB, String engine) {
        TtsDeckSettingsUpdateCommand toCommand() {
            return new TtsDeckSettingsUpdateCommand(autoSpeakA, autoSpeakB, engine);
        }
    }
}
