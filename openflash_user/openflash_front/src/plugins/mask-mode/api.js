import { request } from '../../db/database.js'

/** mask-mode 卡包设置变更事件名。 */
export const MASK_MODE_DECK_SETTINGS_CHANGED_EVENT = 'mask-mode:deck-settings-changed'

/** 卡包级 mask-mode 设置默认值，后端缺字段时回退到此。enabled 默认 true 与 DB 列默认对齐。 */
export const DEFAULT_DECK_MASK_MODE_SETTINGS = { mode: 'random', enabled: true }

/**
 * 通知运行中的 mask-mode 插件刷新指定卡包的设置缓存。
 */
export function dispatchDeckMaskModeSettingsChanged(deckId, settings) {
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(MASK_MODE_DECK_SETTINGS_CHANGED_EVENT, {
            detail: { deckId, settings },
        }))
    }
}

/**
 * 读取指定卡包的 mask-mode 设置，后端缺字段时回退到插件默认值。
 */
export async function getDeckMaskModeSettings(deckId) {
    const settings = await request(`/api/plugins/mask-mode/decks/${deckId}/settings`)
    return settings ? { ...DEFAULT_DECK_MASK_MODE_SETTINGS, ...settings } : { ...DEFAULT_DECK_MASK_MODE_SETTINGS }
}

/**
 * 保存指定卡包的完整 mask-mode 设置，返回合并默认值后的结果。
 */
export async function saveDeckMaskModeSettings(deckId, settingsPayload) {
    const settings = await request(`/api/plugins/mask-mode/decks/${deckId}/settings`, {
        method: 'PUT',
        body: JSON.stringify(settingsPayload),
    })
    return settings ? { ...DEFAULT_DECK_MASK_MODE_SETTINGS, ...settings } : { ...DEFAULT_DECK_MASK_MODE_SETTINGS, ...settingsPayload }
}
