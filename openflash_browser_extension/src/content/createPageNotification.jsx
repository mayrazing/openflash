import PageNotificationToast from './PageNotificationToast.jsx'
import { createShadowKonstaRoot } from '../ui/createShadowKonstaRoot.jsx'

const DISPLAY_DURATION_MS = 2300

function runImmediately(callback) {
  callback()
}

/** 创建页面保存通知控制器。 */
export function createPageNotification(deps = {}) {
  const pageDocument = deps.document || document
  const createShadowRoot = deps.createShadowRoot || createShadowKonstaRoot
  const scheduleFrame = deps.requestAnimationFrame
    || globalThis.requestAnimationFrame?.bind(globalThis)
    || runImmediately
  const scheduleTimeout = deps.setTimeout || globalThis.setTimeout.bind(globalThis)
  const cancelTimeout = deps.clearTimeout || globalThis.clearTimeout.bind(globalThis)
  let host = null
  let konstaRoot = null
  let notification = null
  let opened = false
  let hideTimer = null
  let nextId = 0

  function ensureHost() {
    if (host) return
    host = pageDocument.createElement('div')
    host.style.position = 'fixed'
    host.style.inset = '0'
    host.style.zIndex = '2147483647'
    host.style.pointerEvents = 'none'
    konstaRoot = createShadowRoot(host)
    pageDocument.body.appendChild(host)
  }

  function render() {
    konstaRoot.render(
      <PageNotificationToast notification={notification} opened={opened} />,
    )
  }

  return {
    /** 显示本次保存结果并重置关闭计时。 */
    show(nextNotification) {
      ensureHost()
      const id = nextId + 1
      nextId = id
      notification = {
        id,
        message: String(nextNotification?.message || ''),
        level: nextNotification?.level || 'success',
      }
      opened = false
      render()
      if (hideTimer !== null) cancelTimeout(hideTimer)
      scheduleFrame(() => {
        if (notification?.id !== id) return
        opened = true
        render()
      })
      hideTimer = scheduleTimeout(() => {
        if (notification?.id !== id) return
        opened = false
        hideTimer = null
        render()
      }, DISPLAY_DURATION_MS)
    },

    /** 清理通知计时器、React 根节点和 Shadow host。 */
    destroy() {
      nextId += 1
      if (hideTimer !== null) cancelTimeout(hideTimer)
      hideTimer = null
      konstaRoot?.unmount()
      host?.remove()
      host = null
      konstaRoot = null
      notification = null
      opened = false
    },
  }
}
