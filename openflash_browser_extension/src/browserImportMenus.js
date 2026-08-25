import { ROOT_MENU_ID } from './config.js'
import { resolveDefaultDeckId } from './defaultDeckState.js'
import { t } from './i18n.js'

export function getDeckIdForMenuClick(menuItemId, defaultDeckId) {
  const id = String(menuItemId || '')
  if (id === ROOT_MENU_ID) {
    return defaultDeckId || null
  }
  return null
}

export function createBrowserImportMenus(deps) {
  let refreshPromise = null
  let refreshAgain = false
  let currentDefaultDeckId = null

  async function refreshMenus() {
    if (refreshPromise) {
      refreshAgain = true
      return refreshPromise
    }
    refreshPromise = (async () => {
      let lastError = null
      do {
        refreshAgain = false
        try {
          await refreshMenusOnce()
          lastError = null
        } catch (error) {
          lastError = error
        }
      } while (refreshAgain)
      if (lastError) throw lastError
    })().finally(() => {
      refreshPromise = null
    })
    return refreshPromise
  }

  async function refreshMenusOnce() {
    await deps.contextMenus.removeAll()
    let decks = []
    try {
      const baseUrl = await deps.getServiceUrl()
      decks = await deps.api.decks(baseUrl)
      const resolved = Array.isArray(decks)
        ? resolveDefaultDeckId(decks, await deps.storage.getDefaultDeckId())
        : { defaultDeckId: null, shouldClear: false }
      currentDefaultDeckId = resolved.defaultDeckId
      if (resolved.shouldClear) {
        await deps.storage.setDefaultDeckId(null)
      }
    } catch (error) {
      currentDefaultDeckId = null
      throw error
    }

    deps.contextMenus.create({
      id: ROOT_MENU_ID,
      title: t('menu.title'),
      contexts: ['all'],
    })
  }

  async function handleMenuClick(info, tab) {
    let defaultDeckId = info?.menuItemId === ROOT_MENU_ID ? currentDefaultDeckId : null
    if (info?.menuItemId === ROOT_MENU_ID && !defaultDeckId) {
      const storedDefaultDeckId = await deps.storage.getDefaultDeckId()
      if (storedDefaultDeckId) {
        const baseUrl = await deps.getServiceUrl()
        const decks = await deps.api.decks(baseUrl)
        const resolved = Array.isArray(decks)
          ? resolveDefaultDeckId(decks, storedDefaultDeckId)
          : { defaultDeckId: null, shouldClear: false }
        if (resolved.shouldClear) {
          await deps.storage.setDefaultDeckId(null)
          await deps.notify(t('menu.defaultDeckUnavailable'), 'warning', tab?.id)
          return
        }
        currentDefaultDeckId = resolved.defaultDeckId
        defaultDeckId = currentDefaultDeckId
      }
    }
    if (info?.menuItemId === ROOT_MENU_ID && !defaultDeckId) {
      await deps.notify(t('menu.defaultDeckRequired'), 'warning', tab?.id)
      return
    }
    const deckId = getDeckIdForMenuClick(info?.menuItemId, defaultDeckId)
    if (!deckId) {
      return
    }
    if (!tab?.id) {
      await deps.notify(t('menu.noAvailablePage'), 'warning', tab?.id)
      return
    }
    await deps.importSelectionToDeck(tab, deckId, info)
  }

  return {
    currentDefaultDeckId: () => currentDefaultDeckId,
    handleMenuClick,
    refreshMenus,
  }
}
