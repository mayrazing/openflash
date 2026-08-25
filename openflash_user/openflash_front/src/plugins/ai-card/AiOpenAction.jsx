import { withGenericClick } from '../../lib/soundEngine'

/** 卡片列表中整张卡的 AI 打开入口。 */
export default function createAiOpenAction({ card, title, deckId } = {}) {
  if (!card?.id) return {}

  /** 派发 AI 卡片打开事件，由 ai-card 全局管理器接管弹窗。 */
  function openAi() {
    window.dispatchEvent(new CustomEvent('ai-card:open', {
      detail: { cardId: card.id, title: title || card.sideA || '', deckId },
    }))
  }

  return { onOpen: withGenericClick(openAi) }
}
