package openflash_ai_runtime.entity;

/** 只允许 runtime mapper 读写的加密平台凭证. */
public record PlatformAiSecret(long connectionId, String secretEnc) {

    @Override
    public String toString() {
        return "PlatformAiSecret[connectionId=" + connectionId + ", secretEnc=<redacted>]";
    }
}
