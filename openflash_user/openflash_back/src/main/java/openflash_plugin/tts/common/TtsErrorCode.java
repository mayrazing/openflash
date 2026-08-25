package openflash_plugin.tts.common;

import openflash_core.common.AppErrorCode;

/**
 * TTS 插件专属错误码。实现 core 的 AppErrorCode 契约，随插件目录存放，
 * 新增/删除 TTS 错误码不再触碰 core。value 沿用历史值，保证前端编码不变。
 */
public enum TtsErrorCode implements AppErrorCode {

    TTS_TEXT_BLANK(40040),
    TTS_TEXT_TOO_LONG(40041),
    TTS_ENGLISH_ONLY(40042),
    TTS_BUSY(42901),
    TTS_UPSTREAM_ERROR(50207),
    TTS_ADDRESS_NOT_LOCAL(50302);

    private final int value;

    TtsErrorCode(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }
}
