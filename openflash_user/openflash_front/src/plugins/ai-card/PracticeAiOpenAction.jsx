import { withGenericClick } from '../../lib/soundEngine'

/** 练习卡面整块区域的 AI 打开入口。 */
export default function createPracticeAiOpenAction({ card, side, text, deckId } = {}) {
  if (!card?.id) return {}

  /** 派发带卡面信息的 AI 卡片打开事件，由 ai-card 全局管理器接管弹窗。 */
  function openAi() {
    window.dispatchEvent(new CustomEvent('ai-card:open', {
      detail: { cardId: card.id, side, title: text, deckId },
    }))
  }

  return { onOpen: withGenericClick(openAi) }
}
