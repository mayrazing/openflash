/**
 * mask-mode eligibility 缓存模块单测。
 *
 * 测试基建：node:test，无 jsdom。本模块顶层会引用 window 注册事件监听，
 * 因此在 import 前先伪造一个最小 window（addEventListener + dispatchEvent）。
 */

import test from 'node:test'
import assert from 'node:assert/strict'

// 在 import 模块前注入最小 window stub，让模块顶层 if (typeof window !== 'undefined') 分支生效。
const listeners = new Map() // eventName -> Set<fn>
const fakeWindow = {
  addEventListener(name, fn) {
    if (!listeners.has(name)) listeners.set(name, new Set())
    listeners.get(name).add(fn)
  },
  removeEventListener(name, fn) {
    listeners.get(name)?.delete(fn)
  },
  dispatchEvent(event) {
    const set = listeners.get(event.type)
    if (!set) return true
    for (const fn of set) fn(event)
    return true
  },
}
globalThis.window = fakeWindow

const cacheModule = await import('./eligibilityCache.js')
const {
  getCachedEligibility,
  setCachedEligibility,
  invalidateDeckCache,
} = cacheModule

const { MASK_MODE_DECK_SETTINGS_CHANGED_EVENT } = await import('./api.js')
const { TTS_DECK_SETTINGS_CHANGED_EVENTS } = await import('../tts/api.js')

test('set 后 get 可读出同一对象引用', () => {
  const v = { eligible: true, mode: 'full' }
  setCachedEligibility('deck-1', 'a', v)
  assert.equal(getCachedEligibility('deck-1', 'a'), v)
})

test('未命中返回 undefined', () => {
  assert.equal(getCachedEligibility('deck-nope', 'a'), undefined)
})

test('a/b 两面独立存储互不干扰', () => {
  setCachedEligibility('deck-2', 'a', { eligible: true, mode: 'random' })
  setCachedEligibility('deck-2', 'b', { eligible: false, mode: 'full' })
  assert.deepEqual(getCachedEligibility('deck-2', 'a'), { eligible: true, mode: 'random' })
  assert.deepEqual(getCachedEligibility('deck-2', 'b'), { eligible: false, mode: 'full' })
})

test('invalidateDeckCache 清掉该 deckId 的 a、b 两面', () => {
  setCachedEligibility('deck-3', 'a', { eligible: true, mode: 'full' })
  setCachedEligibility('deck-3', 'b', { eligible: true, mode: 'full' })
  invalidateDeckCache('deck-3')
  assert.equal(getCachedEligibility('deck-3', 'a'), undefined)
  assert.equal(getCachedEligibility('deck-3', 'b'), undefined)
})

test('invalidateDeckCache 不影响其它 deck', () => {
  setCachedEligibility('deck-keep', 'a', { eligible: true, mode: 'full' })
  setCachedEligibility('deck-drop', 'a', { eligible: true, mode: 'full' })
  invalidateDeckCache('deck-drop')
  assert.equal(getCachedEligibility('deck-drop', 'a'), undefined)
  assert.deepEqual(getCachedEligibility('deck-keep', 'a'), { eligible: true, mode: 'full' })
})

test('invalidateDeckCache(null) 安全且无副作用', () => {
  setCachedEligibility('deck-safe', 'a', { eligible: true, mode: 'full' })
  invalidateDeckCache(null)
  invalidateDeckCache(undefined)
  assert.deepEqual(getCachedEligibility('deck-safe', 'a'), { eligible: true, mode: 'full' })
})

test('MASK_MODE_DECK_SETTINGS_CHANGED_EVENT 触发后失效对应 deckId 缓存', () => {
  setCachedEligibility('deck-mask-evt', 'a', { eligible: true, mode: 'full' })
  setCachedEligibility('deck-mask-evt', 'b', { eligible: true, mode: 'full' })
  setCachedEligibility('deck-other', 'a', { eligible: true, mode: 'full' })

  fakeWindow.dispatchEvent({
    type: MASK_MODE_DECK_SETTINGS_CHANGED_EVENT,
    detail: { deckId: 'deck-mask-evt' },
  })

  assert.equal(getCachedEligibility('deck-mask-evt', 'a'), undefined)
  assert.equal(getCachedEligibility('deck-mask-evt', 'b'), undefined)
  assert.deepEqual(getCachedEligibility('deck-other', 'a'), { eligible: true, mode: 'full' })
})

test('TTS_DECK_SETTINGS_CHANGED_EVENTS 触发后失效对应 deckId 缓存', () => {
  setCachedEligibility('deck-tts-evt', 'a', { eligible: true, mode: 'full' })
  setCachedEligibility('deck-tts-evt', 'b', { eligible: false, mode: 'random' })

  fakeWindow.dispatchEvent({
    type: TTS_DECK_SETTINGS_CHANGED_EVENTS[0],
    detail: { deckId: 'deck-tts-evt' },
  })

  assert.equal(getCachedEligibility('deck-tts-evt', 'a'), undefined)
  assert.equal(getCachedEligibility('deck-tts-evt', 'b'), undefined)
})

test('事件 detail 缺 deckId 时不抛错', () => {
  setCachedEligibility('deck-quiet', 'a', { eligible: true, mode: 'full' })
  fakeWindow.dispatchEvent({ type: MASK_MODE_DECK_SETTINGS_CHANGED_EVENT, detail: {} })
  fakeWindow.dispatchEvent({ type: TTS_DECK_SETTINGS_CHANGED_EVENTS[0] })
  // 未指定 deckId → 不清任何缓存。
  assert.deepEqual(getCachedEligibility('deck-quiet', 'a'), { eligible: true, mode: 'full' })
})
