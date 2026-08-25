const CONTEXT_KEY_PREFIX = 'manualCardContext:'
const WINDOW_ID_KEY = 'manualCardWindowId'
const MAX_SELECTED_TEXT_LENGTH = 10_000
const MAX_LABEL_LENGTH = 1_000

/** 管理扩展自有的手动建卡窗口, 页面只从 session storage 领取一次上下文. */
export function createManualCardWindowManager(deps) {
  return {
    async open(rawContext) {
      const stored = await deps.storageSession.get(WINDOW_ID_KEY)
      const existingWindowId = stored[WINDOW_ID_KEY]
      if (Number.isInteger(existingWindowId)) {
        try {
          await deps.windows.update(existingWindowId, { focused: true })
          return { reused: true, windowId: existingWindowId }
        } catch {
          await deps.storageSession.remove(WINDOW_ID_KEY)
        }
      }

      const token = deps.randomUUID()
      const contextKey = `${CONTEXT_KEY_PREFIX}${token}`
      await deps.storageSession.set({ [contextKey]: normalizeContext(rawContext) })
      try {
        const editorWindow = await deps.windows.create({
          focused: true,
          height: 450,
          type: 'popup',
          url: deps.runtime.getURL(`manualCard.html?context=${encodeURIComponent(token)}`),
          width: 480,
        })
        if (Number.isInteger(editorWindow?.id)) {
          await deps.storageSession.set({ [WINDOW_ID_KEY]: editorWindow.id })
        }
        return { reused: false, windowId: editorWindow?.id }
      } catch (error) {
        await deps.storageSession.remove(contextKey)
        throw error
      }
    },
  }
}

/** 从 URL 中读取随机句柄并原子式删除对应上下文, 防止重复领取. */
export async function consumeManualCardWindowContext({ search, storageSession }) {
  const token = new URLSearchParams(search).get('context')
  if (!token || !/^[a-zA-Z0-9_-]{1,128}$/.test(token)) return null

  const contextKey = `${CONTEXT_KEY_PREFIX}${token}`
  const stored = await storageSession.get(contextKey)
  await storageSession.remove(contextKey)
  if (!stored[contextKey]) return null

  try {
    return normalizeContext(stored[contextKey])
  } catch {
    return null
  }
}

/** 只接受来自当前扩展 manualCard.html 的内部保存消息. */
export function isTrustedManualCardSender(sender, pageUrl) {
  if (!sender?.url) return false
  try {
    const expected = new URL(pageUrl)
    const actual = new URL(sender.url)
    return actual.protocol === expected.protocol
      && actual.host === expected.host
      && actual.pathname === expected.pathname
  } catch {
    return false
  }
}

function normalizeContext(context) {
  if (!context || typeof context !== 'object') throw new Error('invalid manual card context')
  const baseUrl = normalizeBaseUrl(context.baseUrl)
  const deckId = String(context.deckId || '').trim()
  if (!deckId || deckId.length > 128) throw new Error('invalid manual card deck')
  const sourceTabId = context.sourceTabId
  if (sourceTabId != null && (!Number.isInteger(sourceTabId) || sourceTabId <= 0)) {
    throw new Error('invalid manual card source tab')
  }

  const labels = {}
  if (context.labels && typeof context.labels === 'object') {
    for (const [key, value] of Object.entries(context.labels)) {
      if (typeof value !== 'string') continue
      labels[String(key).slice(0, 128)] = value.slice(0, MAX_LABEL_LENGTH)
    }
  }
  return {
    baseUrl,
    deckId,
    ...(sourceTabId == null ? {} : { sourceTabId }),
    labels,
    selectedText: typeof context.selectedText === 'string'
      ? context.selectedText.slice(0, MAX_SELECTED_TEXT_LENGTH)
      : '',
  }
}

function normalizeBaseUrl(value) {
  const url = new URL(String(value || ''))
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('invalid manual card service URL')
  return url.href.replace(/\/$/, '')
}
