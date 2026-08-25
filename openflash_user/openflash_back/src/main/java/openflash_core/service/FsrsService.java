package openflash_core.service;

import openflash_core.entity.CardProgress;

/**
 * 负责调用 FSRS 库进行调度。
 */
public interface FsrsService {

    /**
     * 根据当前进度和用户评分，返回新的学习进度。
     */
    CardProgress schedule(CardProgress currentProgress, Integer rating, Double targetRetention);
}
