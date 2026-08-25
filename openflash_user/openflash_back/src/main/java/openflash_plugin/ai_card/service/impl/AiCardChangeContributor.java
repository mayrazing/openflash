package openflash_plugin.ai_card.service.impl;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import openflash_core.spi.CardChangeEvent;

/** 将核心卡片变化事件转换为 AI 卡片插件后台任务。 */
@Component
public class AiCardChangeContributor {

    private final CardAiCacheTaskProducer cardAiCacheTaskProducer;
    private final CardSideCompletionTaskProducer cardSideCompletionTaskProducer;
    private final AiCardFeatureGuard featureGuard;
    private final AiCardInstallGate installGate;

    public AiCardChangeContributor(
            CardAiCacheTaskProducer cardAiCacheTaskProducer,
            CardSideCompletionTaskProducer cardSideCompletionTaskProducer,
            AiCardFeatureGuard featureGuard,
            AiCardInstallGate installGate) {
        this.cardAiCacheTaskProducer = cardAiCacheTaskProducer;
        this.cardSideCompletionTaskProducer = cardSideCompletionTaskProducer;
        this.featureGuard = featureGuard;
        this.installGate = installGate;
    }

    /** 按卡片变化类型触发 AI 缓存预热和另一面补全；仅对已装 ai-card 的卡包生效。 */
    @EventListener
    public void afterCardsChanged(CardChangeEvent event) {
        if (event.cardIds().isEmpty()) {
            return;
        }
        // 全局开关在最前：关时直接 return，不查库
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        // 迁移事件不触发 AI 任务：卡片内容未变，复用既有缓存与补全
        if (event.kind() == CardChangeEvent.Kind.MOVED) {
            return;
        }
        // 门控唯一出口：只保留已装 ai-card 卡包的卡片
        List<Long> allowed = installGate.retainInstalledDeckCards(event.cardIds());
        if (allowed.isEmpty()) {
            return;
        }
        cardAiCacheTaskProducer.triggerCardsAfterCommit(allowed, event.userId());
        // 安装门控与 kind 过滤是「与」关系：side-completion 仍需满足 kind 条件
        if (featureGuard.isSideCompletionEnabled()
                && (event.kind() == CardChangeEvent.Kind.CREATED
                        || event.kind() == CardChangeEvent.Kind.IMPORTED
                        || event.kind() == CardChangeEvent.Kind.UPDATED)) {
            cardSideCompletionTaskProducer.triggerCardsAfterCommit(allowed, event.userId());
        }
    }
}
