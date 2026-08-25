import { badgeColors } from './ui/appleColors.generated.js'

const badgeColorByLevel = {
  success: badgeColors.success,
  warning: badgeColors.warning,
  error: badgeColors.error,
}

/** 创建导入结果通知器，统一分发持久状态、badge 与页面 HUD。 */
export function createImportNotifier(deps) {
  let clearBadgeTimer = null

  /** 页面接收器可能因插件重载失效，补装后重试一次。 */
  async function notifyPage(tabId, message, level) {
    const payload = {
      type: 'OPENFLASH_SHOW_NOTIFICATION',
      message,
      level,
    }
    try {
      await deps.tabs.sendMessage(tabId, payload)
    } catch (error) {
      if (!deps.ensurePageReceiver) throw error
      await deps.ensurePageReceiver(tabId)
      await deps.tabs.sendMessage(tabId, payload)
    }
  }

  /** 分发一次导入结果，并在稍后清除 badge。 */
  async function notify(message, level = 'success', tabId = null) {
    const deliveries = [
      Promise.resolve().then(() => deps.setLastImportStatus({ message, level, at: deps.now() })),
      Promise.resolve().then(() => deps.action.setBadgeText({ text: level === 'success' ? '✓' : '!' })),
      Promise.resolve().then(() => deps.action.setBadgeBackgroundColor({
        color: badgeColorByLevel[level] || badgeColors.error,
      })),
    ]
    if (tabId != null) {
      deliveries.push(Promise.resolve().then(() => notifyPage(tabId, message, level)))
    }
    // 任一反馈渠道失败都不能覆盖真实保存结果，其他渠道继续完成。
    await Promise.allSettled(deliveries)
    if (clearBadgeTimer !== null) {
      deps.clearTimeout(clearBadgeTimer)
    }
    clearBadgeTimer = deps.setTimeout(() => {
      deps.action.setBadgeText({ text: '' }).catch(() => {})
      clearBadgeTimer = null
    }, 2500)
  }

  return notify
}

/** 创建面向当前活动页的通知入口，供插件弹窗复用后台 HUD。 */
export function createActiveTabNotifier(deps) {
  /** 把结果发送到指定来源页；普通弹窗没有来源页时回退当前活动页。 */
  return async function notifyActiveTab(message, level = 'success', sourceTabId = null) {
    let targetTabId = sourceTabId
    if (!Number.isInteger(targetTabId) || targetTabId <= 0) {
      const [tab] = await deps.tabs.query({ active: true, currentWindow: true })
      targetTabId = tab?.id
    }
    await deps.notify(message, level, targetTabId)
  }
}
