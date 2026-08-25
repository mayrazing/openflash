export const MANUAL_CARD_COMMAND = 'openflash-manual-card'

/**
 * 创建手动建卡命令处理器：校验功能、登录态、默认卡包存在性，然后打开扩展自有窗口。
 */
export function createManualCardCommandHandler(deps) {
  return async function handleManualCardCommand(command, tab) {
    if (command !== MANUAL_CARD_COMMAND) return false
    if (!tab?.id) return true

    const baseUrl = await deps.getServiceUrl()
    const deckId = await deps.getDefaultDeckId()
    if (!deckId) {
      await deps.notify(deps.t('menu.defaultDeckRequired'), 'error', tab.id)
      return true
    }

    await deps.ensureBrowserImportEnabled(baseUrl)
    const decks = await deps.listDecks(baseUrl)
    if (!decks.some((deck) => String(deck.id) === String(deckId))) {
      await deps.setDefaultDeckId(null)
      await deps.notify(deps.t('menu.defaultDeckUnavailable'), 'error', tab.id)
      return true
    }

    let selectedText = ''
    try {
      selectedText = await deps.readSelectedText(tab.id)
    } catch {
      // chrome:// 等受保护页面不可读取选区, 仍允许用户打开空白编辑器.
    }
    await deps.openEditor({
      deckId,
      baseUrl,
      sourceTabId: tab.id,
      labels: manualCardLabels(deps.t),
      selectedText,
    })
    return true
  }
}

function manualCardLabels(t) {
  return [
    'manualCard.title',
    'manualCard.sideA',
    'manualCard.sideB',
    'manualCard.save',
    'manualCard.saving',
    'manualCard.cancel',
    'manualCard.unsavedTitle',
    'manualCard.unsavedConfirm',
    'manualCard.unsavedBack',
    'manualCard.emptyContent',
    'manualCard.imageProcessFailed',
    'manualCard.imageTooLarge',
    'manualCard.imagesTooLarge',
    'manualCard.tooManyImages',
    'manualCard.saveFailed',
    'manualCard.saved',
  ].reduce((labels, key) => {
    labels[key] = t(key)
    return labels
  }, {})
}
