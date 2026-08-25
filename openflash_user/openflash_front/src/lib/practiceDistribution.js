// 判断分布图卡片是否对应当前正在练习的卡片，用于给整张小格加呼吸亮。
export function getDistributionCardActiveClass(cardId, activeCardId) {
  if (activeCardId == null) return ''
  return String(cardId) === String(activeCardId) ? 'practice-distribution-card-active' : ''
}
