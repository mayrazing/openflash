import { request } from '../../db/database.js'

export const DEFAULT_DECK_AI_SETTINGS = {
  aiExplanationEnabledA: false,
  aiExplanationEnabledB: false,
  aiExplanationPromptA: null,
  aiExplanationPromptB: null,
  aiCompletionEnabled: false,
  aiCompletionPrompt: null,
}

/** 构建 AI 缓存状态接口路径，供测试验证 URL 兼容。 */
export function buildAiCacheStatusPath(cardId, side) {
  return side
    ? `/api/cards/${cardId}/ai-cache-status?side=${encodeURIComponent(side)}`
    : `/api/cards/${cardId}/ai-cache-status`
}

/** 构建 AI 缓存重生成接口路径，供测试验证 URL 兼容。 */
export function buildAiCacheRegeneratePath(cardId, side) {
  return side
    ? `/api/cards/${cardId}/ai-cache-regenerate?side=${encodeURIComponent(side)}`
    : `/api/cards/${cardId}/ai-cache-regenerate`
}

/** 读取 AI 插件子功能开关状态，供插件 UI 决定展示范围。 */
export async function getAiFeatureState() {
  const state = await request('/api/plugins/ai-card/features')
  return {
    sideCompletionEnabled: state?.sideCompletionEnabled === true,
  }
}

/** 读取指定卡包的 AI 设置，后端缺字段时用前端默认值补齐。 */
export async function getDeckAiSettings(deckId) {
  const settings = await request(`/api/decks/${deckId}/ai-settings`)
  return settings
    ? { ...DEFAULT_DECK_AI_SETTINGS, ...settings }
    : { ...DEFAULT_DECK_AI_SETTINGS }
}

/** 保存指定卡包的完整 AI 设置；PUT 要提交完整字段，后端返回空数据时用提交内容覆盖默认值。 */
export async function saveDeckAiSettings(deckId, settingsPayload) {
  const settings = await request(`/api/decks/${deckId}/ai-settings`, {
    method: 'PUT',
    body: JSON.stringify(settingsPayload),
  })

  return settings
    ? { ...DEFAULT_DECK_AI_SETTINGS, ...settings }
    : { ...DEFAULT_DECK_AI_SETTINGS, ...settingsPayload }
}

/** 查询 AI 缓存状态。命中返回 hit，未命中返回 queued。 */
export async function checkAiCacheStatus(cardId, side) {
  return request(buildAiCacheStatusPath(cardId, side))
}

/** 强制重新生成 AI 解释；已有缓存会被后台任务覆盖。 */
export async function regenerateAiCache(cardId, side) {
  return request(buildAiCacheRegeneratePath(cardId, side), {
    method: 'POST',
  })
}
