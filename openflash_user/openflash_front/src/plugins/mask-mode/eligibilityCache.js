/**
 * mask-mode 资格缓存（模块级单例）。
 *
 * 职责：跨 React 组件实例、跨 prefetch 与 overlay 共享一个按 `${deckId}:${questionSide}`
 * 索引的资格缓存（{ eligible, mode }）。原本写在 QuestionFaceMaskOverlay.jsx 顶层，
 * 现在抽出来：prefetch 在进入练习前预热写入，overlay 同步读取，避免首帧闪烁。
 *
 * 监听 mask-mode/tts 卡包设置变更事件：触发后失效对应 deckId 的两面缓存，
 * 防止旧设置驱动新渲染。
 *
 * 注册去重：JS 模块本就是单例，理论上 import 只执行一次；额外加 registered flag
 * 是显式防御，防止 HMR / 测试热重载场景下被重复注册导致同一事件触发多次失效。
 */

import { MASK_MODE_DECK_SETTINGS_CHANGED_EVENT } from './api.js'
import { TTS_DECK_SETTINGS_CHANGED_EVENTS } from '../tts/api.js'

/** 缓存主体：key=`${deckId}:${questionSide}`，value={eligible, mode}。 */
const cache = new Map()

/** 生成缓存 key，集中拼接逻辑避免多处复制。 */
function cacheKey(deckId, questionSide) {
  return `${deckId}:${questionSide}`
}

/**
 * 读缓存：命中返回 { eligible, mode }，未命中返回 undefined。
 * 选择 undefined（而非 null）以匹配 Map.get 原生语义，让调用方用 `??` 判空更直观。
 */
export function getCachedEligibility(deckId, questionSide) {
  if (deckId == null) return undefined
  return cache.get(cacheKey(deckId, questionSide))
}

/** 写缓存：调用方负责保证 value 形如 { eligible, mode }。 */
export function setCachedEligibility(deckId, questionSide, value) {
  if (deckId == null) return
  cache.set(cacheKey(deckId, questionSide), value)
}

/**
 * 失效指定卡包的全部资格缓存（按前缀匹配 a、b 两面）。
 * deckId 为空时静默 noop，便于事件分发兜底。
 */
export function invalidateDeckCache(deckId) {
  if (deckId == null) return
  const prefix = `${deckId}:`
  for (const key of cache.keys()) {
    if (key.startsWith(prefix)) cache.delete(key)
  }
}

/** 设置变更事件处理：仅依赖 event.detail.deckId，缺失则忽略，不抛错。 */
function handleDeckSettingsChanged(event) {
  invalidateDeckCache(event?.detail?.deckId)
}

/**
 * 模块级一次性注册事件监听。
 * 用 globalThis flag 显式防重，避免极端场景（HMR、循环 import）下重复绑定。
 */
const LISTENER_KEY = Symbol.for('pw.mask-mode.eligibilityCache.listenerRegistered')
if (typeof window !== 'undefined' && !globalThis[LISTENER_KEY]) {
  window.addEventListener(MASK_MODE_DECK_SETTINGS_CHANGED_EVENT, handleDeckSettingsChanged)
  for (const eventName of TTS_DECK_SETTINGS_CHANGED_EVENTS) {
    window.addEventListener(eventName, handleDeckSettingsChanged)
  }
  globalThis[LISTENER_KEY] = true
}
