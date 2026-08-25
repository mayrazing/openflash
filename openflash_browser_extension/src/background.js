import { ROOT_MENU_ID } from './config.js'
import { createBrowserImportMenus } from './browserImportMenus.js'
import { createCommandImportHandler } from './commandImport.js'
import { createManualCardCommandHandler } from './manualCardCommand.js'
import { createActiveTabNotifier, createImportNotifier } from './importNotifier.js'
import { api, ensureBrowserImportEnabled, uploadImageFile } from './apiClient.js'
import { createManualCardBackgroundSaveHandler } from './manualCardBackgroundSave.js'
import { createManualCardWindowManager, isTrustedManualCardSender } from './manualCardWindow.js'
import { importImages } from './imageImport.js'
import { readSelectionWithContextImage } from './contextMenuSelection.js'
import { getDefaultDeckId, getServiceUrl, setDefaultDeckId, setLastImportStatus } from './storage.js'
import { resetLanguage, setLanguage, t } from './i18n.js'
import { openShortcutSetupOnInstall } from './shortcutSetupInstall.js'

let languageLoadPromise = null

/** 为插件重载前已打开的网页恢复提示接收器。 */
async function ensurePageNotificationReceiver(tabId) {
  await chrome.scripting.executeScript({
    target: { tabId },
    files: ['assets/contentScript.js'],
  })
}

const notify = createImportNotifier({
  setLastImportStatus,
  action: chrome.action,
  tabs: chrome.tabs,
  ensurePageReceiver: ensurePageNotificationReceiver,
  now: Date.now,
  setTimeout: globalThis.setTimeout.bind(globalThis),
  clearTimeout: globalThis.clearTimeout.bind(globalThis),
})
const notifyActiveTab = createActiveTabNotifier({ tabs: chrome.tabs, notify })

/**
 * 从服务端加载用户语言偏好并应用到当前 i18n 上下文。
 * 加载失败时回退为默认语言 'en'。
 */
async function loadLanguageFromSettings() {
  resetLanguage()
  try {
    const baseUrl = await getServiceUrl()
    const settings = await api.settings(baseUrl)
    setLanguage(settings?.language)
  } catch {
    resetLanguage()
  }
}

/**
 * 合并并发语言加载，避免冷启动时多入口同时请求设置。
 */
function ensureLanguageFromSettings() {
  if (!languageLoadPromise) {
    languageLoadPromise = loadLanguageFromSettings().finally(() => {
      languageLoadPromise = null
    })
  }
  return languageLoadPromise
}

const importMenus = createBrowserImportMenus({
  contextMenus: chrome.contextMenus,
  storage: {
    getDefaultDeckId,
    setDefaultDeckId,
  },
  api,
  getServiceUrl,
  importSelectionToDeck,
  notify,
})

chrome.runtime.onInstalled.addListener((details) => {
  refreshMenus().catch(() => createErrorMenu())
  openShortcutSetupOnInstall(details, { runtime: chrome.runtime, tabs: chrome.tabs })
})

chrome.runtime.onStartup.addListener(() => {
  refreshMenus().catch(() => createErrorMenu())
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES' || message?.type === 'OPENFLASH_MANUAL_CARD_CREATE') {
    handleManualCardBackgroundSave(message, sender)
      .then((response) => sendResponse(response))
      .catch((error) => sendResponse({ ok: false, message: error.message, code: error.code }))
    return true
  }
  if (message?.type === 'OPENFLASH_NOTIFY_ACTIVE_TAB') {
    notifyActiveTab(
      message.message,
      message.level,
      isManualCardPageSender(sender) ? message.sourceTabId : null,
    )
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({ ok: false, message: error.message }))
    return true
  }
  if (message?.type !== 'OPENFLASH_REFRESH_MENUS') {
    return false
  }
  refreshMenus().then(() => sendResponse({ ok: true })).catch((error) => {
    sendResponse({ ok: false, message: error.message })
  })
  return true
})

chrome.contextMenus.onClicked.addListener((info, tab) => {
  ensureLanguageFromSettings()
    .then(() => importMenus.handleMenuClick(info, tab))
    .catch((error) => notify(error.message || t('import.failed'), 'error', tab?.id))
})

const handleCommandImport = createCommandImportHandler(importMenus)

const manualCardWindow = createManualCardWindowManager({
  storageSession: chrome.storage.session,
  windows: chrome.windows,
  runtime: chrome.runtime,
  randomUUID: () => crypto.randomUUID(),
})

const handleManualCardCommand = createManualCardCommandHandler({
  getServiceUrl,
  getDefaultDeckId,
  setDefaultDeckId,
  ensureBrowserImportEnabled,
  listDecks: api.decks,
  readSelectedText: readSelectedTextFromTab,
  openEditor: manualCardWindow.open,
  notify,
  t,
})

const handleManualCardBackgroundSave = createManualCardBackgroundSaveHandler({
  uploadImageFile,
  createImportedCard: api.createImportedCard,
  isTrustedSender: isManualCardPageSender,
})

chrome.commands.onCommand.addListener((command, tab) => {
  ensureLanguageFromSettings()
    .then(async () => {
      if (await handleManualCardCommand(command, tab)) return
      await handleCommandImport(command, tab)
    })
    .catch((error) => notify(error.message || t('import.failed'), 'error', tab?.id))
})

async function refreshMenus() {
  await ensureLanguageFromSettings()
  return importMenus.refreshMenus()
}

function createErrorMenu() {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: ROOT_MENU_ID,
      title: t('menu.title'),
      contexts: ['all'],
      enabled: false,
    })
  })
}

async function importSelectionToDeck(tab, deckId, menuInfo) {
  const baseUrl = await getServiceUrl()
  await ensureBrowserImportEnabled(baseUrl)
  const selection = await readSelectionWithContextImage(
    menuInfo,
    () => extractSelection(tab.id),
    () => extractSelectionByInjection(tab.id),
  )
  const { sideAImage, failedCount } = await importImages(baseUrl, selection.imageSources)
  const sideA = selection.sideA || ''
  if (!sideA.trim() && sideAImage.length === 0) {
    throw new Error(t('import.emptyContent'))
  }
  await api.createImportedCard(baseUrl, deckId, { sideA, sideAImage })
  await notify(
    failedCount > 0 ? t('notification.partialSaved', { count: failedCount }) : t('notification.saved'),
    failedCount > 0 ? 'warning' : 'success',
    tab.id,
  )
}

async function extractSelection(tabId) {
  const response = await chrome.tabs.sendMessage(tabId, { type: 'OPENFLASH_EXTRACT_SELECTION' })
  if (!response?.ok) {
    throw new Error(t('import.selectionReadFailed'))
  }
  return response.selection || { sideA: '', imageSources: [] }
}

async function extractSelectionByInjection(tabId) {
  const [result] = await chrome.scripting.executeScript({
    target: { tabId },
    func: extractCurrentSelectionFromPage,
  })
  return result?.result || { sideA: '', imageSources: [] }
}

async function readSelectedTextFromTab(tabId) {
  const [result] = await chrome.scripting.executeScript({
    target: { tabId },
    func: readSelectedTextFromPage,
  })
  return typeof result?.result === 'string' ? result.result : ''
}

function readSelectedTextFromPage() {
  return String(window.getSelection?.()?.toString?.() || '').slice(0, 10_000)
}

function isManualCardPageSender(sender) {
  return isTrustedManualCardSender(sender, chrome.runtime.getURL('manualCard.html'))
}

function extractCurrentSelectionFromPage() {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    return { sideA: '', imageSources: [] }
  }

  const maxImages = 20
  const maxTextLength = 100_000
  const maxRemoteSourceLength = 8_192
  const maxDataSourceLength = Math.ceil(8 * 1024 * 1024 * 4 / 3) + 1024
  const maxTotalSourceLength = 20 * 1024 * 1024
  const maxScannedElements = 10_000
  const sideA = String(selection.toString?.() || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxTextLength)
  const imageSources = []
  const seenImages = new Set()
  let scannedElements = 0
  let retainedSourceLength = 0

  ranges: for (let rangeIndex = 0; rangeIndex < selection.rangeCount; rangeIndex += 1) {
    const range = selection.getRangeAt(rangeIndex)
    let root = range.commonAncestorContainer
    if (root?.nodeType === 3) root = root.parentElement
    if (!root) continue
    const walker = document.createTreeWalker(root, globalThis.NodeFilter?.SHOW_ELEMENT || 1)
    let node = root.nodeType === 1 ? root : walker.nextNode()
    while (node) {
      scannedElements += 1
      if (scannedElements > maxScannedElements) break ranges
      if (String(node.tagName || '').toLowerCase() === 'img' && !seenImages.has(node)) {
        seenImages.add(node)
        let intersects = false
        try {
          intersects = range.intersectsNode(node)
        } catch {
          intersects = false
        }
        if (intersects) {
          const source = normalizeImageSourceForSelection(
            node.currentSrc || node.getAttribute?.('src') || '',
            document.baseURI,
          )
          const allowed = /^https?:\/\//i.test(source)
            ? source.length <= maxRemoteSourceLength
            : /^(data|blob):/i.test(source) && source.length <= maxDataSourceLength
          if (allowed) {
            if (retainedSourceLength + source.length > maxTotalSourceLength) break ranges
            imageSources.push(source)
            retainedSourceLength += source.length
          }
          if (imageSources.length >= maxImages) break ranges
        }
      }
      node = walker.nextNode()
    }
  }
  return { sideA, imageSources }

  function normalizeImageSourceForSelection(source, baseUrl) {
    const value = String(source || '').trim()
    if (!value || /^(data|blob):/i.test(value)) {
      return value
    }
    try {
      return baseUrl ? new URL(value, baseUrl).href : value
    } catch {
      return value
    }
  }
}
