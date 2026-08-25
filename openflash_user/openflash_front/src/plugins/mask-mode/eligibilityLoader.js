import { resolveMaskEligibility } from './maskEligibility.js'
import { getDeckMaskModeSettings } from './api.js'
import { readAnyTtsAutoSpeak } from '../tts/api.js'
import { setCachedEligibility } from './eligibilityCache.js'

/** 读取并缓存单面遮蔽资格。 */
export async function loadAndCacheMaskEligibility({
  deckId,
  questionSide,
  installedIds,
  getMaskSettings = getDeckMaskModeSettings,
  getTtsSettings,
}) {
  const ttsReader = getTtsSettings ?? (() => readAnyTtsAutoSpeak(deckId, installedIds))
  const result = await resolveMaskEligibility({
    deckId,
    questionSide,
    installedIds,
    getDeckMaskModeSettings: getMaskSettings,
    getDeckTtsSettings: ttsReader,
  })
  setCachedEligibility(deckId, questionSide, result)
  return result
}
