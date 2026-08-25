package openflash_plugin.ai_card.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import openflash_core.entity.Card;
import openflash_core.mapper.CardMapper;
import openflash_core.service.PluginInstallService;
import openflash_plugin.ai_card.config.AiCardPluginDescriptor;

/**
 * AI 卡片插件「按卡包安装」门控。
 * 门控逻辑唯一出口：只返回所属卡包已安装 ai-card 插件的卡片 id，其余一律丢弃。
 */
@Component
public class AiCardInstallGate {

    private final CardMapper cardMapper;
    private final PluginInstallService pluginInstallService;

    public AiCardInstallGate(CardMapper cardMapper, PluginInstallService pluginInstallService) {
        this.cardMapper = cardMapper;
        this.pluginInstallService = pluginInstallService;
    }

    /**
     * 判断某卡包是否已安装 ai-card 插件——本插件安装判断的唯一出口（绑定 PLUGIN_ID）。
     */
    public boolean isInstalledOnDeck(Long deckId) {
        return pluginInstallService.isInstalledOnDeck(deckId, AiCardPluginDescriptor.PLUGIN_ID);
    }

    /**
     * 过滤卡片：只保留所属卡包已安装 ai-card 的卡片 id。
     * 以 findByIds 查询结果为准（查不到的 cardId 自然丢弃）；同一卡包安装状态只查一次。
     */
    public List<Long> retainInstalledDeckCards(Collection<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        List<Card> cards = cardMapper.findByIds(cardIds);
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> installedByDeck = new HashMap<>();
        List<Long> retained = new ArrayList<>();
        for (Card card : cards) {
            boolean installed = installedByDeck.computeIfAbsent(card.getDeckId(), this::isInstalledOnDeck);
            if (installed) {
                retained.add(card.getId());
            }
        }
        return retained;
    }
}
