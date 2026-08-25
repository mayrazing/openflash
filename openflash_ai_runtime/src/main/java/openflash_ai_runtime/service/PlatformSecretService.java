package openflash_ai_runtime.service;

public interface PlatformSecretService {

    void replace(String connectionKey, String apiKey);

    String requirePlaintext(long connectionId);
}
