package openflash_plugin.tts.dto;

/** 承载 TTS 卡包设置页一次完整保存. */
public record TtsDeckSettingsUpdateCommand(Boolean autoSpeakA, Boolean autoSpeakB, String engine) {
}
