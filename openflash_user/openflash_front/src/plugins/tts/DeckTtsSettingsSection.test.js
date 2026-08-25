import test from 'node:test'
import assert from 'node:assert/strict'
import { createDeckTtsSettingsSaveController } from './deckTtsSettingsSaveController.js'

test('DeckTtsSettingsSection save success publishes settings changed event payload', async () => {
  const saved = []
  const changed = []
  const settingsPayload = { autoSpeakA: true, autoSpeakB: false }
  const controller = createDeckTtsSettingsSaveController({
    deckId: 42,
    saveDeckTtsSettings: async (deckId, payload) => {
      saved.push({ deckId, payload })
    },
    onChanged: (deckId, payload) => {
      changed.push({ deckId, payload })
    },
    onSaved: () => {},
    onError: () => {},
    isActive: () => true,
    setTimeoutFn: () => 'timer',
    clearTimeoutFn: () => {},
  })

  controller.schedule(settingsPayload)
  await controller.flush({ allowStateUpdates: true })

  assert.deepEqual(saved, [{ deckId: 42, payload: settingsPayload }])
  assert.deepEqual(changed, [{ deckId: 42, payload: settingsPayload }])
})

test('DeckTtsSettingsSection cleanup flushes latest debounced payload without UI state updates', async () => {
  const saved = []
  const savedNotices = []
  const clearedTimers = []
  const firstPayload = { autoSpeakA: true, autoSpeakB: false }
  const latestPayload = { autoSpeakA: true, autoSpeakB: true }
  const controller = createDeckTtsSettingsSaveController({
    deckId: 42,
    saveDeckTtsSettings: async (deckId, payload) => {
      saved.push({ deckId, payload })
    },
    onChanged: () => {},
    onSaved: (payload) => {
      savedNotices.push(payload)
    },
    onError: () => {},
    isActive: () => false,
    setTimeoutFn: () => 'timer',
    clearTimeoutFn: (timer) => {
      clearedTimers.push(timer)
    },
  })

  controller.schedule(firstPayload)
  controller.schedule(latestPayload)
  await controller.dispose()

  assert.deepEqual(saved, [{ deckId: 42, payload: latestPayload }])
  assert.deepEqual(savedNotices, [])
  assert.deepEqual(clearedTimers, ['timer', 'timer'])
})

test('DeckTtsSettingsSection dispose waits for in-flight save to consume pending payload', async () => {
  const saved = []
  const changed = []
  const savedNotices = []
  const pendingResolves = []
  const firstPayload = { autoSpeakA: true, autoSpeakB: false }
  const latestPayload = { autoSpeakA: false, autoSpeakB: true }
  let timerCallback = null
  const controller = createDeckTtsSettingsSaveController({
    deckId: 42,
    saveDeckTtsSettings: async (deckId, payload) => {
      saved.push({ deckId, payload })
      return new Promise(resolve => pendingResolves.push(resolve))
    },
    onChanged: (deckId, payload) => {
      changed.push({ deckId, payload })
    },
    onSaved: (payload) => {
      savedNotices.push(payload)
    },
    onError: () => {},
    isActive: () => true,
    setTimeoutFn: (callback) => {
      timerCallback = callback
      return 'timer'
    },
    clearTimeoutFn: () => {},
  })

  controller.schedule(firstPayload)
  timerCallback()
  assert.deepEqual(saved, [{ deckId: 42, payload: firstPayload }])
  controller.schedule(latestPayload)
  const disposePromise = controller.dispose()

  pendingResolves[0]()
  await waitForNextTurn()
  assert.deepEqual(saved, [
    { deckId: 42, payload: firstPayload },
    { deckId: 42, payload: latestPayload },
  ])
  pendingResolves[1]()
  await disposePromise

  assert.deepEqual(changed, [
    { deckId: 42, payload: firstPayload },
    { deckId: 42, payload: latestPayload },
  ])
  assert.deepEqual(savedNotices, [])
})

function waitForNextTurn() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
