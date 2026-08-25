import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_DECK_MASK_MODE_SETTINGS,
  dispatchDeckMaskModeSettingsChanged,
  getDeckMaskModeSettings,
  saveDeckMaskModeSettings,
  MASK_MODE_DECK_SETTINGS_CHANGED_EVENT,
} from './api.js'

const originalFetch = globalThis.fetch
const originalWindow = globalThis.window
const originalCustomEvent = globalThis.CustomEvent

afterEach(() => {
  globalThis.fetch = originalFetch
  globalThis.window = originalWindow
  globalThis.CustomEvent = originalCustomEvent
})

test('DEFAULT_DECK_MASK_MODE_SETTINGS 默认 mode=random、enabled=true', () => {
  assert.deepEqual(DEFAULT_DECK_MASK_MODE_SETTINGS, { mode: 'random', enabled: true })
})

test('getDeckMaskModeSettings 请求 mask-mode 卡包设置端点', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/plugins/mask-mode/decks/42/settings')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['Content-Type'], 'application/json')
    return response({ code: 200, data: { deckId: 42, mode: 'full' } })
  }

  const settings = await getDeckMaskModeSettings(42)

  // 后端回传 mode=full，合并默认值后仍为 full。
  assert.equal(settings.mode, 'full')
})

test('getDeckMaskModeSettings 后端缺 mode 字段时回退默认值', async () => {
  globalThis.fetch = async () => response({ code: 200, data: { deckId: 42 } })

  const settings = await getDeckMaskModeSettings(42)

  // 后端缺 mode，合并默认值后回退 random。
  assert.equal(settings.mode, DEFAULT_DECK_MASK_MODE_SETTINGS.mode)
})

test('getDeckMaskModeSettings 后端返回 null 时回退默认值', async () => {
  globalThis.fetch = async () => response({ code: 200, data: null })

  const settings = await getDeckMaskModeSettings(42)

  assert.deepEqual(settings, { mode: 'random', enabled: true })
})

test('saveDeckMaskModeSettings 发送完整 payload', async () => {
  const settingsPayload = { mode: 'full' }
  let capturedBody = null
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/plugins/mask-mode/decks/42/settings')
    assert.equal(options.method, 'PUT')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['Content-Type'], 'application/json')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: { deckId: 42, mode: 'full' } })
  }

  const saved = await saveDeckMaskModeSettings(42, settingsPayload)

  // 请求体必须与传入 payload 一致。
  assert.deepEqual(capturedBody, settingsPayload)
  // 后端回传合并默认值后 mode 仍为 full。
  assert.equal(saved.mode, 'full')
})

test('saveDeckMaskModeSettings 后端返回 null 时回退 payload', async () => {
  globalThis.fetch = async () => response({ code: 200, data: null })

  const saved = await saveDeckMaskModeSettings(42, { mode: 'full', enabled: false })

  assert.deepEqual(saved, { mode: 'full', enabled: false })
})

test('dispatchDeckMaskModeSettingsChanged 派发卡包设置变更事件', () => {
  const events = []
  globalThis.CustomEvent = class {
    constructor(type, options) {
      this.type = type
      this.detail = options?.detail
    }
  }
  globalThis.window = {
    dispatchEvent(event) {
      events.push(event)
    },
  }

  const settingsPayload = { mode: 'full' }
  dispatchDeckMaskModeSettingsChanged(42, settingsPayload)

  assert.equal(events[0].type, MASK_MODE_DECK_SETTINGS_CHANGED_EVENT)
  assert.equal(events[0].type, 'mask-mode:deck-settings-changed')
  assert.deepEqual(events[0].detail, { deckId: 42, settings: settingsPayload })
})

test('dispatchDeckMaskModeSettingsChanged 无 window 时安全跳过', () => {
  delete globalThis.window
  // 不应抛错。
  dispatchDeckMaskModeSettingsChanged(42, { mode: 'full' })
})

function response(body) {
  return {
    status: 200,
    ok: true,
    text: async () => JSON.stringify(body),
  }
}
