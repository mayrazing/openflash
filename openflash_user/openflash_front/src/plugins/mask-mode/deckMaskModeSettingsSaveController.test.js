import test from 'node:test'
import assert from 'node:assert/strict'
import { createDeckMaskModeSettingsSaveController } from './deckMaskModeSettingsSaveController.js'

/** mask-mode 保存控制器测试: 只测保存调度/事件派发/失败回退/卸载或换包后回调抑制。 */

function makeController(overrides = {}) {
  const saved = []
  const changedEvents = []
  const successCalls = []
  const errorCalls = []
  let mounted = true
  let currentDeckId = 42
  const opts = {
    deckId: 42,
    saveDeckMaskModeSettings: async (deckId, payload) => {
      saved.push({ deckId, payload })
    },
    onChanged: (deckId, settings) => {
      changedEvents.push({ deckId, settings })
    },
    onSuccess: (payload) => {
      successCalls.push(payload)
    },
    onError: (error) => {
      errorCalls.push(error)
    },
    getCurrentDeckId: () => currentDeckId,
    isMounted: () => mounted,
    ...overrides,
  }
  const controller = createDeckMaskModeSettingsSaveController(opts)
  return {
    controller,
    saved,
    changedEvents,
    successCalls,
    errorCalls,
    setMounted: (v) => { mounted = v },
    setCurrentDeckId: (v) => { currentDeckId = v },
  }
}

test('save success calls saveDeckMaskModeSettings once with mode+enabled payload', async () => {
  const ctx = makeController()
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.saved, [{ deckId: 42, payload: { mode: 'full', enabled: true } }])
})

test('save success dispatches onChanged with full {mode, enabled} settings', async () => {
  const ctx = makeController()
  await ctx.controller.save({ mode: 'random', enabled: false })
  assert.deepEqual(ctx.changedEvents, [
    { deckId: 42, settings: { mode: 'random', enabled: false } },
  ])
})

test('save success calls onSuccess with the saved payload', async () => {
  const ctx = makeController()
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.successCalls, [{ mode: 'full', enabled: true }])
  assert.deepEqual(ctx.errorCalls, [])
})

test('save failure does not dispatch onChanged, calls onError, leaves no success', async () => {
  const boom = new Error('save failed')
  const ctx = makeController({
    saveDeckMaskModeSettings: async () => { throw boom },
  })
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.changedEvents, [])
  assert.deepEqual(ctx.successCalls, [])
  assert.deepEqual(ctx.errorCalls, [boom])
})

test('mode change and enabled change both go through save with the other field carried', async () => {
  const ctx = makeController()
  await ctx.controller.save({ mode: 'full', enabled: true })
  await ctx.controller.save({ mode: 'full', enabled: false })
  assert.deepEqual(ctx.saved, [
    { deckId: 42, payload: { mode: 'full', enabled: true } },
    { deckId: 42, payload: { mode: 'full', enabled: false } },
  ])
  assert.deepEqual(ctx.changedEvents, [
    { deckId: 42, settings: { mode: 'full', enabled: true } },
    { deckId: 42, settings: { mode: 'full', enabled: false } },
  ])
})

test('save success but deckId switched away: skip onSuccess and onChanged', async () => {
  const ctx = makeController()
  ctx.setCurrentDeckId(999) // user switched deck before save resolved
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.saved, [{ deckId: 42, payload: { mode: 'full', enabled: true } }])
  assert.deepEqual(ctx.changedEvents, [])
  assert.deepEqual(ctx.successCalls, [])
})

test('save success but unmounted: skip onSuccess and onChanged', async () => {
  const ctx = makeController()
  ctx.setMounted(false)
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.changedEvents, [])
  assert.deepEqual(ctx.successCalls, [])
})

test('save failure but unmounted: skip onError', async () => {
  const ctx = makeController({
    saveDeckMaskModeSettings: async () => { throw new Error('x') },
  })
  ctx.setMounted(false)
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.errorCalls, [])
})

test('save failure but deckId switched: skip onError', async () => {
  const ctx = makeController({
    saveDeckMaskModeSettings: async () => { throw new Error('x') },
  })
  ctx.setCurrentDeckId(999)
  await ctx.controller.save({ mode: 'full', enabled: true })
  assert.deepEqual(ctx.errorCalls, [])
})
