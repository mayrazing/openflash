import { anyTtsInstalled } from '../tts/api.js'

/**
 * 题目面遮蔽资格判断（纯函数模块）。
 *
 * 职责：判断「当前这道题的题目面该不该参与 mask-mode 遮蔽」。
 * 只有当 tts 已安装、且该面启用了自动发音时，才有资格遮蔽。
 *
 * 纯函数约束：
 * - 不调 React hook、不读 DOM、不读网络、不调 isEnglish()。
 * - 遮蔽设置与 TTS 设置的读取函数由调用方注入（依赖注入，便于测试）。
 * - 不等待或监听实际音频播放结果，只看静态开关。
 *
 * 依赖约定（对齐项目实际取值）：
 * - questionSide：小写 'a' / 'b'（见 PracticeCard.jsx、practiceQueue.js）。
 * - installedIds：数组，用 anyTtsInstalled 判断是否装了任意 TTS 引擎插件。
 * - TTS 自动发音字段：autoSpeakA / autoSpeakB（见 plugins/tts/api.js DEFAULT_DECK_TTS_SETTINGS）。
 * - 遮蔽设置 mode 取值：'random' | 'full'，非法值回退 'random'。
 */

/** 合法遮蔽模式白名单，非法值一律回退 random。 */
const VALID_MASK_MODES = ['random', 'full']

function normalizeMaskMode(rawMode) {
  return VALID_MASK_MODES.includes(rawMode) ? rawMode : 'random'
}

/** 用已加载的 mask/TTS 设置计算单面遮蔽资格，供 prefetch 复用一次性读取结果。 */
export function resolveMaskEligibilityFromSettings({
  questionSide,
  installedIds,
  maskSettings,
  ttsSettings,
}) {
  const mode = normalizeMaskMode(maskSettings?.mode)
  // 卡包级总开关显式 false 时直接停用，不读 tts；缺字段或 null 不视为关闭。
  if (maskSettings?.enabled === false) {
    return { eligible: false, mode }
  }
  const ttsInstalled = anyTtsInstalled(installedIds)
  if (!ttsInstalled) {
    return { eligible: false, mode }
  }

  const autoSpeak = questionSide === 'a'
    ? ttsSettings?.autoSpeakA === true
    : questionSide === 'b'
      ? ttsSettings?.autoSpeakB === true
      : false

  return { eligible: autoSpeak, mode }
}

/**
 * 判断当前题目面是否有资格参与 mask-mode 遮蔽。
 *
 * 决策流程：
 * 1. 读遮蔽设置（失败或非法 mode 回退 random）——mode 始终随结果返回。
 * 2. TTS 未安装 → 无资格，不再读 TTS 设置（避免无意义调用）。
 * 3. 读 TTS 设置（失败视为该面未启用自动发音）。
 * 4. 按题目面取对应自动发音开关：A 面→autoSpeakA，B 面→autoSpeakB，非 a/b 一律视为关闭。
 *
 * @param {object} params
 * @param {string} params.deckId - 卡包 ID。
 * @param {'a'|'b'} params.questionSide - 当前题目面。
 * @param {string[]} params.installedIds - 已安装插件 ID 数组。
 * @param {(deckId: string) => Promise<{mode?: string}>} params.getDeckMaskModeSettings - 遮蔽设置读取函数。
 * @param {(deckId: string) => Promise<{autoSpeakA?: boolean, autoSpeakB?: boolean}>} params.getDeckTtsSettings - TTS 设置读取函数。
 * @returns {Promise<{ eligible: boolean, mode: 'random'|'full' }>}
 */
export async function resolveMaskEligibility({
  deckId,
  questionSide,
  installedIds,
  getDeckMaskModeSettings,
  getDeckTtsSettings,
}) {
  // 1. 读遮蔽设置，失败或返回非法 mode 时回退 random；同时记录卡包级总开关。
  let mode = 'random'
  let enabledExplicitlyOff = false
  try {
    const maskSettings = await getDeckMaskModeSettings(deckId)
    const rawMode = maskSettings?.mode
    mode = normalizeMaskMode(rawMode)
    // 只有显式 false 才视为关闭；缺字段/null 不误判。
    enabledExplicitlyOff = maskSettings?.enabled === false
  } catch {
    mode = 'random'
  }

  // 1.5 卡包级总开关关闭 → 直接停用，不读 TTS。
  if (enabledExplicitlyOff) {
    return { eligible: false, mode }
  }

  // 2. TTS 未安装 → 无资格（不读 TTS 设置，省一次调用）。
  const ttsInstalled = anyTtsInstalled(installedIds)
  if (!ttsInstalled) {
    return { eligible: false, mode }
  }

  // 3. 读 TTS 设置，失败视为该面未启用自动发音。
  let ttsSettings = null
  try {
    ttsSettings = await getDeckTtsSettings(deckId)
  } catch {
    ttsSettings = null
  }

  // 4. 按题目面取对应自动发音开关，非 a/b 一律视为关闭。
  const autoSpeak = questionSide === 'a'
    ? ttsSettings?.autoSpeakA === true
    : questionSide === 'b'
      ? ttsSettings?.autoSpeakB === true
      : false

  return { eligible: autoSpeak, mode }
}
