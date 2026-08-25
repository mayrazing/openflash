/** 创建插件弹窗通知发送器，把动态提示交给后台统一展示。 */
export function createPopupNotifier(runtime) {
  /** 发送一条用户可读的动态提示。 */
  return function notify(message, level = 'success') {
    return runtime.sendMessage({
      type: 'OPENFLASH_NOTIFY_ACTIVE_TAB',
      message,
      level,
    })
  }
}

/** 创建统一弹窗状态出口，确保动态状态同时进入页面 HUD。 */
export function createPopupStatusPresenter(state, notify) {
  /** 更新指定状态字段并发布非空提示。 */
  function present(field, message, level) {
    const value = String(message || '')
    state[field] = value
    return value
      ? Promise.resolve().then(() => notify(value, level)).catch(() => {})
      : Promise.resolve()
  }

  return {
    /** 展示普通错误。 */
    error: (message) => present('error', message, 'error'),
    /** 展示 AI 设置错误。 */
    aiError: (message) => present('aiSettingsError', message, 'error'),
    /** 展示成功消息。 */
    success: (message) => present('message', message, 'success'),
  }
}
