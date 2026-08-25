/**
 * 创建练习页自动朗读事件控制器，集中处理缓存失效和异步乱序。
 */
export function createAutoSpeakController({ getDeckTtsSettings, isEnglish, speakText }) {
  const settingsCache = new Map()
  const cacheVersions = new Map()
  let latestFaceToken = 0

  /** 返回当前卡包缓存版本；设置变更会递增版本。 */
  function getCacheVersion(cacheKey) {
    return cacheVersions.get(cacheKey) ?? 0
  }

  /** 读取卡包 TTS 设置；同一控制器生命周期内按 deckId 缓存。 */
  async function loadDeckSettings(deckId) {
    const cacheKey = String(deckId)
    if (settingsCache.has(cacheKey)) {
      return settingsCache.get(cacheKey)
    }
    const requestVersion = getCacheVersion(cacheKey)
    const settings = await getDeckTtsSettings(deckId)
    if (requestVersion !== getCacheVersion(cacheKey)) {
      return settingsCache.has(cacheKey) ? settingsCache.get(cacheKey) : settings
    }
    settingsCache.set(cacheKey, settings)
    return settings
  }

  /** 设置保存后刷新缓存；没有 settings 时删除缓存，下次重新读取。 */
  function handleDeckSettingsChanged(event) {
    const { deckId, settings } = event.detail ?? {}
    if (!deckId) return
    const cacheKey = String(deckId)
    cacheVersions.set(cacheKey, getCacheVersion(cacheKey) + 1)
    if (settings) {
      settingsCache.set(cacheKey, settings)
    } else {
      settingsCache.delete(cacheKey)
    }
  }

  /** 收到卡面展示事件后，只允许最新卡面的异步结果触发朗读。 */
  async function handlePracticeFaceShown(event) {
    // 无效后续事件也递增 token，防止旧请求迟到后朗读旧卡面。
    const faceToken = ++latestFaceToken
    const { deckId, side, text } = event.detail ?? {}
    if (!deckId || !side || !text) return
    if (side !== 'a' && side !== 'b') return

    try {
      const settings = await loadDeckSettings(deckId)
      if (faceToken !== latestFaceToken) return
      const shouldSpeak = side === 'a' ? settings.autoSpeakA : settings.autoSpeakB
      if (shouldSpeak && isEnglish(text)) {
        speakText(text, { deckId }).catch(() => {})
      }
    } catch {
      // 自动朗读失败不能打断练习流程。
    }
  }

  return {
    handleDeckSettingsChanged,
    handlePracticeFaceShown,
  }
}
