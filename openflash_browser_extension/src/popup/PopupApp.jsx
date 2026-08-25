import { useCallback, useEffect, useRef, useState } from 'react'
import { api, DEFAULT_DECK_AI_SETTINGS } from '../apiClient.js'
import { openBrowserShortcutSettings } from '../browserShortcutSettings.js'
import { resetLanguage, setLanguage, t } from '../i18n.js'
import { setDefaultDeckFromPopup, syncDefaultDeckAfterDeckLoad } from '../popupDefaultDeckActions.js'
import { resolvePopupErrorMessage, resolveSessionErrorMessage } from '../popupErrorMessage.js'
import { createPopupNotifier, createPopupStatusPresenter } from '../popupNotification.js'
import { buildAuthUrl, persistServiceUrlValue } from '../serviceUrlForm.js'
import {
  getDefaultDeckId,
  getLastImportStatus,
  getSelectedDeckId,
  getServiceUrl,
  setDefaultDeckId,
  setLastImportStatus,
  setSelectedDeckId,
  setServiceUrl,
} from '../storage.js'
import PopupView from './PopupView.jsx'

const initialState = {
  serviceUrl: '',
  user: null,
  decks: [],
  selectedDeckId: null,
  defaultDeckId: null,
  aiSettings: { ...DEFAULT_DECK_AI_SETTINGS },
  aiSettingsError: '',
  lastImportStatus: null,
  message: '',
  error: '',
  shortcuts: [],
  pendingDeleteDeckId: null,
}

export default function PopupApp({ chromeApi = chrome }) {
  const [state, setReactState] = useState(initialState)
  const stateRef = useRef(initialState)

  const updateState = useCallback((nextState) => {
    const next = typeof nextState === 'function' ? nextState(stateRef.current) : nextState
    stateRef.current = next
    setReactState(next)
    return next
  }, [])

  const presentStatus = useCallback((method, message) => {
    const next = { ...stateRef.current }
    const popupStatus = createPopupStatusPresenter(next, createPopupNotifier(chromeApi.runtime))
    const publishing = popupStatus[method](message)
    updateState(next)
    return publishing
  }, [chromeApi, updateState])

  const presentError = useCallback((error, target = 'error', fallback = t('import.failed')) => {
    const message = resolvePopupErrorMessage(error, fallback)
    return presentStatus(target === 'aiSettingsError' ? 'aiError' : 'error', message)
  }, [presentStatus])

  const loadLanguageSafely = useCallback(async (serviceUrl) => {
    resetLanguage()
    document.documentElement.lang = 'en'
    try {
      const settings = await api.settings(serviceUrl)
      const lang = setLanguage(settings?.language)
      document.documentElement.lang = lang
    } catch {
      resetLanguage()
      document.documentElement.lang = 'en'
    }
  }, [])

  const loadAiSettingsSafely = useCallback(async (serviceUrl, selectedDeckId) => {
    try {
      const aiSettings = selectedDeckId
        ? await api.getAiSettings(serviceUrl, selectedDeckId)
        : DEFAULT_DECK_AI_SETTINGS
      updateState((current) => ({
        ...current,
        aiSettings: { ...DEFAULT_DECK_AI_SETTINGS, ...(aiSettings || {}) },
        aiSettingsError: '',
      }))
    } catch (error) {
      updateState((current) => ({
        ...current,
        aiSettings: { ...DEFAULT_DECK_AI_SETTINGS },
      }))
      await presentError(error, 'aiSettingsError', t('errors.50301'))
    }
  }, [presentError, updateState])

  const refreshContextMenus = useCallback(async () => {
    const response = await chromeApi.runtime.sendMessage({ type: 'OPENFLASH_REFRESH_MENUS' })
    if (!response?.ok) throw new Error(response?.message || t('import.failed'))
  }, [chromeApi])

  const loadSession = useCallback(async () => {
    let languageLoaded = false
    const serviceUrl = stateRef.current.serviceUrl
    try {
      const user = await api.me(serviceUrl)
      updateState((current) => ({ ...current, user, error: '' }))
      await loadLanguageSafely(serviceUrl)
      languageLoaded = true

      const decks = await api.decks(serviceUrl)
      const defaultDeckState = await syncDefaultDeckAfterDeckLoad(
        decks,
        stateRef.current.defaultDeckId,
        { setDefaultDeckId },
      )
      let selectedDeckId = stateRef.current.selectedDeckId
      const selectedDeckExists = decks.some((deck) => String(deck.id) === String(selectedDeckId))
      if (!selectedDeckExists) {
        selectedDeckId = decks[0] ? String(decks[0].id) : null
        await setSelectedDeckId(selectedDeckId)
      }
      updateState((current) => ({
        ...current,
        decks,
        selectedDeckId,
        defaultDeckId: defaultDeckState.defaultDeckId,
      }))
      await loadAiSettingsSafely(serviceUrl, selectedDeckId)
      chromeApi.runtime.sendMessage({ type: 'OPENFLASH_REFRESH_MENUS' }).catch(() => {})
    } catch (error) {
      if (!languageLoaded) {
        resetLanguage()
        document.documentElement.lang = 'en'
      }
      updateState((current) => ({ ...current, user: null, decks: [] }))
      await presentError(resolveSessionErrorMessage(error, t('import.failed')))
    }
  }, [chromeApi, loadAiSettingsSafely, loadLanguageSafely, presentError, updateState])

  const loadShortcutsSafely = useCallback(async () => {
    try {
      const shortcuts = await chromeApi.commands.getAll()
      updateState((current) => ({ ...current, shortcuts }))
    } catch {
      updateState((current) => ({ ...current, shortcuts: [] }))
    }
  }, [chromeApi, updateState])

  useEffect(() => {
    async function initialize() {
      resetLanguage()
      const serviceUrl = await getServiceUrl()
      const selectedDeckId = await getSelectedDeckId()
      const defaultDeckId = await getDefaultDeckId()
      const lastImportStatus = await getLastImportStatus()
      updateState((current) => ({
        ...current,
        serviceUrl,
        selectedDeckId,
        defaultDeckId,
        lastImportStatus,
      }))
      await loadSession()
      await loadShortcutsSafely()
    }

    initialize().catch((error) => presentError(error))
  }, [loadSession, loadShortcutsSafely, presentError, updateState])

  const runAction = useCallback(async (action) => {
    try {
      return await action()
    } catch (error) {
      await presentError(error)
      return undefined
    }
  }, [presentError])

  async function persistCurrentServiceUrl() {
    const serviceUrl = await persistServiceUrlValue(stateRef.current.serviceUrl, setServiceUrl)
    updateState((current) => ({ ...current, serviceUrl }))
    return serviceUrl
  }

  const actions = {
    setServiceUrl(value) {
      updateState((current) => ({ ...current, serviceUrl: value }))
      return runAction(async () => {
        return persistServiceUrlValue(value, setServiceUrl)
      })
    },
    login() {
      return runAction(async () => {
        const serviceUrl = await persistCurrentServiceUrl()
        return chromeApi.tabs.create({ url: buildAuthUrl(serviceUrl) })
      })
    },
    refresh() {
      return runAction(async () => {
        await persistCurrentServiceUrl()
        await loadSession()
      })
    },
    logout() {
      return runAction(async () => {
        await api.logout(stateRef.current.serviceUrl)
        updateState((current) => ({
          ...current,
          user: null,
          decks: [],
          selectedDeckId: null,
          defaultDeckId: null,
          lastImportStatus: null,
          message: '',
          error: '',
        }))
        await setSelectedDeckId(null)
        await setDefaultDeckId(null)
        await setLastImportStatus(null)
        chromeApi.runtime.sendMessage({ type: 'OPENFLASH_REFRESH_MENUS' }).catch(() => {})
      })
    },
    createDeck(name) {
      if (!name.trim()) return Promise.resolve(undefined)
      return runAction(async () => {
        const deck = await api.createDeck(stateRef.current.serviceUrl, name.trim())
        const selectedDeckId = String(deck.id)
        updateState((current) => ({ ...current, selectedDeckId }))
        await setSelectedDeckId(selectedDeckId)
        await loadSession()
        return deck
      })
    },
    selectDeck(deckId) {
      return runAction(async () => {
        const selectedDeckId = String(deckId)
        updateState((current) => ({ ...current, selectedDeckId }))
        await setSelectedDeckId(selectedDeckId)
        await loadAiSettingsSafely(stateRef.current.serviceUrl, selectedDeckId)
      })
    },
    toggleDefaultDeck(deckId) {
      return runAction(async () => {
        const result = await setDefaultDeckFromPopup(stateRef.current.defaultDeckId, deckId, {
          setDefaultDeckId,
          refreshMenus: refreshContextMenus,
        })
        updateState((current) => ({ ...current, defaultDeckId: result.defaultDeckId }))
        await presentError(result.refreshError || '')
      })
    },
    // 删除卡包不可撤销, 先记下待删卡包让视图弹确认框, 真正删除只发生在 confirmDeleteDeck。
    requestDeleteDeck(deckId) {
      updateState((current) => ({ ...current, pendingDeleteDeckId: String(deckId) }))
    },
    cancelDeleteDeck() {
      updateState((current) => ({ ...current, pendingDeleteDeckId: null }))
    },
    confirmDeleteDeck() {
      const deckId = stateRef.current.pendingDeleteDeckId
      updateState((current) => ({ ...current, pendingDeleteDeckId: null }))
      if (!deckId) return Promise.resolve(undefined)
      return runAction(async () => {
        await api.deleteDeck(stateRef.current.serviceUrl, deckId)
        if (String(stateRef.current.selectedDeckId) === String(deckId)) {
          updateState((current) => ({ ...current, selectedDeckId: null }))
          await setSelectedDeckId(null)
        }
        if (String(stateRef.current.defaultDeckId) === String(deckId)) {
          updateState((current) => ({ ...current, defaultDeckId: null }))
          await setDefaultDeckId(null)
        }
        await loadSession()
      })
    },
    openShortcutSettings() {
      return runAction(() => openBrowserShortcutSettings(chromeApi.tabs))
    },
    setAiCompletionPrompt(value) {
      updateState((current) => ({
        ...current,
        aiSettings: { ...current.aiSettings, aiCompletionPrompt: value },
      }))
    },
    setAiCompletionEnabled(value) {
      updateState((current) => ({
        ...current,
        aiSettings: { ...current.aiSettings, aiCompletionEnabled: value },
      }))
    },
    saveAiSettings() {
      if (stateRef.current.aiSettingsError) return Promise.resolve(undefined)
      return runAction(async () => {
        const payload = {
          ...stateRef.current.aiSettings,
          aiCompletionEnabled: Boolean(stateRef.current.aiSettings.aiCompletionEnabled),
          aiCompletionPrompt: stateRef.current.aiSettings.aiCompletionPrompt?.trim() || null,
        }
        const aiSettings = await api.saveAiSettings(
          stateRef.current.serviceUrl,
          stateRef.current.selectedDeckId,
          payload,
        )
        updateState((current) => ({ ...current, aiSettings }))
        await presentStatus('success', t('popup.saved'))
      })
    },
  }

  function shortcutText(commandName) {
    const command = state.shortcuts.find((item) => item.name === commandName)
    return command?.shortcut || t('popup.shortcutUnset')
  }

  return <PopupView actions={actions} shortcutText={shortcutText} state={state} t={t} />
}
