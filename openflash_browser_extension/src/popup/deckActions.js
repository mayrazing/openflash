// 返回卡包动作条的点击处理: 点在按钮上什么都不做, 交给按钮自己的 onClick;
// 点在动作条空白处则选中该卡包, 让整行都是选中热区.
export function createDeckActionsClick(selectDeck, deckId) {
  return (event) => {
    if (event.target.closest('button')) return
    selectDeck(deckId)
  }
}
