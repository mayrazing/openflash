import { extractCurrentSelection, extractSelectionFromHtml } from '../selectionAdapter.js'

const CONTENT_SCRIPT_INSTALLATION = Symbol.for('openflash.contentScriptInstallation')

/** 在同一 isolated-world global 上只创建一次控制器和消息监听器。 */
export function installContentScriptOnce(deps = {}) {
  const globalScope = deps.globalScope || globalThis
  if (globalScope[CONTENT_SCRIPT_INSTALLATION]) return globalScope[CONTENT_SCRIPT_INSTALLATION]

  const pageNotification = deps.createPageNotification()
  const removeRuntimeListener = installContentScriptRuntime({
    runtime: deps.runtime,
    pageNotification,
    getSelectionHtml: deps.getSelectionHtml,
  })
  let cleaned = false
  const installation = {
    cleanup() {
      if (cleaned) return
      cleaned = true
      removeRuntimeListener()
      pageNotification.destroy()
      if (globalScope[CONTENT_SCRIPT_INSTALLATION] === installation) {
        delete globalScope[CONTENT_SCRIPT_INSTALLATION]
      }
    },
    pageNotification,
  }
  globalScope[CONTENT_SCRIPT_INSTALLATION] = installation
  return installation
}

/** 安装内容脚本消息监听器并返回清理函数。 */
export function installContentScriptRuntime(deps = {}) {
  const runtime = deps.runtime || chrome.runtime
  const pageNotification = deps.pageNotification
  const getSelection = deps.getSelectionHtml
    ? () => {
        const selection = deps.getSelectionHtml()
        return extractSelectionFromHtml(selection.html, selection.baseUrl)
      }
    : extractCurrentSelection

  function onMessage(message, sender, sendResponse) {
    if (message?.type === 'OPENFLASH_SHOW_NOTIFICATION') {
      pageNotification.show({ message: message.message, level: message.level })
      return false
    }
    if (message?.type !== 'OPENFLASH_EXTRACT_SELECTION') return false

    sendResponse({
      ok: true,
      selection: getSelection(),
    })
    return false
  }

  runtime.onMessage.addListener(onMessage)
  return () => runtime.onMessage.removeListener(onMessage)
}
