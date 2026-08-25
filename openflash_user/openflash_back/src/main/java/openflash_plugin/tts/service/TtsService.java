package openflash_plugin.tts.service;

public interface TtsService {

    record TtsVariantRequest(
        String normalizedText,
        String voice,
        double speed,
        String accent,
        String engine,
        String engineVersion,
        String inFlightKey
    ) {
    }

    /**
     * 返回指定文本对应的音频内容。
     * 服务端只负责实时合成, WAV 持久缓存由浏览器负责.
     */
    default byte[] getAudioBytes(String text) {
        throw new UnsupportedOperationException();
    }

    /** 带当前用户身份读取音频, 用于执行每用户并发限制. */
    default byte[] getAudioBytes(Long ownerUserId, String text) {
        return getAudioBytes(text);
    }

    /** 带卡包上下文读取音频, 由卡包设置决定默认模型. */
    default byte[] getAudioBytes(Long ownerUserId, Long deckId, String text) {
        return getAudioBytes(ownerUserId, text);
    }

    /** 按用户明确选择的模型生成候选音频. */
    default byte[] getAudioBytes(Long ownerUserId, String text, String engine) {
        throw new UnsupportedOperationException();
    }

    /**
     * 根据当前配置构建 TTS 合成请求.
     */
    default TtsVariantRequest createVariantRequest(String text) {
        throw new UnsupportedOperationException();
    }
}
