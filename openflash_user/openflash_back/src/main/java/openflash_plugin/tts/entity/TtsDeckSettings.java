package openflash_plugin.tts.entity;

/** 返回 TTS 插件在卡包内控制自动朗读和默认合成模型的设置. */
public record TtsDeckSettings(Long deckId, Boolean autoSpeakA, Boolean autoSpeakB, String engine) {
}
