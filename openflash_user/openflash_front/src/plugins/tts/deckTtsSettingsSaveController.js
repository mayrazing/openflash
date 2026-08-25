/**
 * 创建 TTS 卡包设置保存控制器，负责 debounce、队列合并和卸载前 flush。
 */
export function createDeckTtsSettingsSaveController({
  deckId,
  saveDeckTtsSettings,
  onChanged,
  onBeforeSave,
  onSaved,
  onError,
  isActive,
  delayMs = 400,
  setTimeoutFn = setTimeout,
  clearTimeoutFn = clearTimeout,
}) {
  let timer = null
  let pendingPayload = null
  let inFlight = false
  let inFlightPromise = null
  let disposed = false

  /** 执行保存队列；allowStateUpdates=false 时只做网络保存和事件通知。 */
  async function persist({ allowStateUpdates }) {
    if (inFlight) return inFlightPromise
    inFlight = true
    inFlightPromise = (async () => {
      try {
        while (pendingPayload) {
          const next = pendingPayload
          pendingPayload = null

          try {
            if (allowStateUpdates && !disposed && isActive()) {
              onBeforeSave?.()
            }
            await saveDeckTtsSettings(deckId, next)
            onChanged?.(deckId, next)
            if (allowStateUpdates && !disposed && isActive()) {
              onSaved?.(next)
            }
          } catch (error) {
            if (allowStateUpdates && !disposed && isActive()) {
              onError?.(error)
            }
          }
        }
      } finally {
        inFlight = false
        inFlightPromise = null
      }
    })()
    return inFlightPromise
  }

  /** 记录最新 payload 并延迟保存；连续变更只保存最后一次。 */
  function schedule(settingsPayload) {
    pendingPayload = settingsPayload
    if (timer) clearTimeoutFn(timer)
    timer = setTimeoutFn(() => {
      timer = null
      persist({ allowStateUpdates: true })
    }, delayMs)
  }

  /** 立即保存当前待保存 payload，用于切换卡包或卸载前防止丢数据。 */
  function flush({ allowStateUpdates = false } = {}) {
    if (timer) {
      clearTimeoutFn(timer)
      timer = null
    }
    return persist({ allowStateUpdates })
  }

  /** 标记控制器已卸载，并 flush 最新 payload，禁止后续 UI 状态更新。 */
  function dispose() {
    disposed = true
    return flush({ allowStateUpdates: false })
  }

  return {
    schedule,
    flush,
    dispose,
  }
}
