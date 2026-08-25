/**
 * 创建 mask-mode 卡包设置保存控制器。
 *
 * 职责：把保存请求 → 成功事件派发 → 失败回调 这条链路从 React Section 中抽出来，
 * 让 Section 只关心 state/UI，让保存调度可被独立测试。
 *
 * 与 TTS controller 的差异：mask-mode 没有 debounce 与队列合并需求（mode/enabled
 * 切换属于单次用户动作，不会高频抖动），所以这里只提供单次 `save`。
 *
 * 卸载/换卡包后保护：通过 isMounted() 与 getCurrentDeckId() === deckId 双闸门，
 * 防止保存返回时回调误打到已卸载组件或别的卡包。
 */
export function createDeckMaskModeSettingsSaveController({
  deckId,
  saveDeckMaskModeSettings,
  onChanged,
  onSuccess,
  onError,
  getCurrentDeckId,
  isMounted,
}) {
  /** 是否仍由当前 Section 实例 + 当前卡包持有；不在则跳过任何 UI 副作用。 */
  function stillOwnsResult() {
    return isMounted() && getCurrentDeckId() === deckId
  }

  /**
   * 全量保存当前 mode + enabled。
   * 成功：派发 onChanged 给运行中的练习页缓存，再调 onSuccess 让 Section 刷新本地 savedRef。
   * 失败：调 onError，由 Section 回退 UI 状态。
   */
  async function save(settingsPayload) {
    try {
      await saveDeckMaskModeSettings(deckId, settingsPayload)
      if (!stillOwnsResult()) return
      onChanged?.(deckId, settingsPayload)
      onSuccess?.(settingsPayload)
    } catch (error) {
      if (!stillOwnsResult()) return
      onError?.(error)
    }
  }

  return { save }
}
