package openflash_plugin.tts.service.impl;

import org.springframework.stereotype.Service;
import openflash_core.entity.AsyncTask;
import openflash_core.service.AsyncTaskHandler;

/**
 * 兼容旧 TTS 预热任务类型；新需求下后台不再预生成发音。
 */
@Service
public class TtsTaskExecutor implements AsyncTaskHandler {

    public static final String TASK_TYPE = "TTS_PREWARM";

    private final TtsFeatureGuard featureGuard;

    /** 注入 TTS 功能 guard，让关闭时历史任务直接跳过。 */
    public TtsTaskExecutor(TtsFeatureGuard featureGuard) {
        this.featureGuard = featureGuard;
    }

    /**
     * 返回该执行器消费的统一任务类型。
     */
    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    /**
     * 直接完成旧预热任务，避免后台再生成发音文件。
     */
    @Override
    public void execute(AsyncTask task) {
        if (!featureGuard.isTtsEnabled()) {
            return;
        }
        // 保留空执行，让历史预热任务完成但不生成音频。
    }
}
