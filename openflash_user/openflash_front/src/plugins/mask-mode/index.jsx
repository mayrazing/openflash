import DeckMaskModeSettingsSection from './DeckMaskModeSettingsSection'
import QuestionFaceMaskOverlay from './QuestionFaceMaskOverlay'
import { resolveMaskEligibilityFromSettings } from './maskEligibility'
import { getDeckMaskModeSettings } from './api'
import { anyTtsInstalled, readAnyTtsAutoSpeak } from '../tts/api'
import { setCachedEligibility } from './eligibilityCache'
import { loadDeckInstalledPlugins } from '../deckInstalledPluginLoader'

const PLUGIN_ID = 'mask-mode'

/**
 * mask-mode 资格预热：进入练习前并行解析 a/b 两面，把结果写进 eligibilityCache。
 *
 * 由 practicePrefetch 调度器调用（fn(deckId, { installedIds }) 形式）。约定：
 * - 卡包未装本插件 → 直接 return，避免无意义读后端；
 *   优先复用 context.installedIds，避免重复调 loadDeckInstalledPlugins。
 * - mask + tts 设置走 Promise.allSettled 并行读，省一个 RTT；任一面失败不抛
 *   （overlay 端未命中缓存会异步走自己的 useEffect 兜底解析，不会因预热失败而崩）。
 *
 * 这里不做"是否装 tts"判断：installedIds 透传给 resolveMaskEligibility，
 * 由它自己决定走 TTS 设置读取还是直接返 eligible=false——保持职责单一。
 */
async function maskModePrefetch(deckId, { installedIds: prefetchedInstalledIds } = {}) {
  if (deckId == null) return

  let installedIds
  try {
    installedIds = Array.isArray(prefetchedInstalledIds)
      ? prefetchedInstalledIds
      : await loadDeckInstalledPlugins(deckId)
  } catch {
    return
  }

  if (!Array.isArray(installedIds) || !installedIds.includes(PLUGIN_ID)) {
    return
  }

  // mask + tts 设置并行读：tts 未装时跳过 tts 这一路。allSettled 让任一失败
  // 都不影响另一路结果，单路失败按 null 兜底，maskEligibility 据此回退。
  const ttsPromise = anyTtsInstalled(installedIds)
    ? readAnyTtsAutoSpeak(deckId, installedIds)
    : Promise.resolve(null)
  const [maskResult, ttsResult] = await Promise.allSettled([
    getDeckMaskModeSettings(deckId),
    ttsPromise,
  ])
  const maskSettings = maskResult.status === 'fulfilled' ? maskResult.value : null
  const ttsSettings = ttsResult.status === 'fulfilled' ? ttsResult.value : null

  for (const side of ['a', 'b']) {
    const result = resolveMaskEligibilityFromSettings({
      questionSide: side,
      installedIds,
      maskSettings,
      ttsSettings,
    })
    setCachedEligibility(deckId, side, result)
  }
}

export default {
  id: PLUGIN_ID,
  slots: {
    'deck-settings.sections': { component: DeckMaskModeSettingsSection, order: 30 },
    'practice.question-face.overlay': { component: QuestionFaceMaskOverlay, order: 30 },
    'practice.prefetch': maskModePrefetch,
  },
}
