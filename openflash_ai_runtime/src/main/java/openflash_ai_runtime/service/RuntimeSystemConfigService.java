package openflash_ai_runtime.service;

public interface RuntimeSystemConfigService {

    String getString(String key, String defaultValue);

    long getLong(String key, long defaultValue);
}
