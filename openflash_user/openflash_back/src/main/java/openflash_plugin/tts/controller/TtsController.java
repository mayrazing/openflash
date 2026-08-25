package openflash_plugin.tts.controller;

import openflash_plugin.tts.service.TtsService;
import openflash_plugin.tts.service.impl.TtsFeatureGuard;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.entity.User;
import openflash_core.service.CurrentUserService;

@RestController
@RequestMapping("/api")
public class TtsController {

    private final TtsService ttsService;
    private final CurrentUserService currentUserService;
    private final TtsFeatureGuard featureGuard;

    public TtsController(
            TtsService ttsService,
            CurrentUserService currentUserService,
            TtsFeatureGuard featureGuard) {
        this.ttsService = ttsService;
        this.currentUserService = currentUserService;
        this.featureGuard = featureGuard;
    }

    /** 普通发音入口, 使用卡包设置中的默认模型. */
    @PostMapping("/tts")
    public ResponseEntity<Resource> getAudio(@RequestBody(required = false) TtsRequest request) {
        featureGuard.ensureTtsEnabled();
        User user = currentUserService.getCurrentUser();
        byte[] audio = ttsService.getAudioBytes(
            user.getId(),
            request == null ? null : request.deckId(),
            request == null ? null : request.text());
        return wav(audio);
    }

    /** CosyVoice3 候选试听入口, 不改变卡包默认模型. */
    @PostMapping("/tts/cosyvoice3")
    public ResponseEntity<Resource> previewCosyvoice3(
            @RequestBody(required = false) TtsRequest request) {
        return preview(request, TtsFeatureGuard.ENGINE_COSYVOICE3);
    }

    /** Piper 候选试听入口, 不改变卡包默认模型. */
    @PostMapping("/tts/piper")
    public ResponseEntity<Resource> previewPiper(
            @RequestBody(required = false) TtsRequest request) {
        return preview(request, TtsFeatureGuard.ENGINE_PIPER);
    }

    private ResponseEntity<Resource> preview(TtsRequest request, String engine) {
        featureGuard.ensureTtsEnabled();
        User user = currentUserService.getCurrentUser();
        byte[] audio = ttsService.getAudioBytes(
            user.getId(), request == null ? null : request.text(), engine);
        return wav(audio);
    }

    private ResponseEntity<Resource> wav(byte[] audio) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/wav"))
            .body(new ByteArrayResource(audio));
    }

    public record TtsRequest(Long deckId, String text) {
    }
}
