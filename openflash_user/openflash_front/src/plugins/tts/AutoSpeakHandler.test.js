import test from 'node:test'
import assert from 'node:assert/strict'
import { createAutoSpeakController } from './autoSpeakController.js'

test('AutoSpeakHandler settings changed event updates cached deck settings', async () => {
  const spoken = []
  let settingsRequestCount = 0
  const controller = createAutoSpeakController({
    getDeckTtsSettings: async () => {
      settingsRequestCount += 1
      return { autoSpeakA: true, autoSpeakB: false }
    },
    isEnglish: () => true,
    speakText: async (text, options) => {
      spoken.push({ text, options })
    },
  })

  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'first' }))
  controller.handleDeckSettingsChanged({
    detail: { deckId: 42, settings: { autoSpeakA: false, autoSpeakB: false } },
  })
  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'muted' }))

  assert.deepEqual(spoken, [{ text: 'first', options: { deckId: 42 } }])
  assert.equal(settingsRequestCount, 1)
})

test('AutoSpeakHandler settings changed event can invalidate cached deck settings', async () => {
  const spoken = []
  const settingsQueue = [
    { autoSpeakA: false, autoSpeakB: false },
    { autoSpeakA: true, autoSpeakB: false },
  ]
  const controller = createAutoSpeakController({
    getDeckTtsSettings: async () => settingsQueue.shift(),
    isEnglish: () => true,
    speakText: async (text) => {
      spoken.push(text)
    },
  })

  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'muted' }))
  controller.handleDeckSettingsChanged({ detail: { deckId: 42 } })
  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'after reload' }))

  assert.deepEqual(spoken, ['after reload'])
})

test('AutoSpeakHandler only speaks latest face when settings requests resolve out of order', async () => {
  const spoken = []
  const pendingSettings = []
  const controller = createAutoSpeakController({
    getDeckTtsSettings: async () => new Promise(resolve => pendingSettings.push(resolve)),
    isEnglish: () => true,
    speakText: async (text) => {
      spoken.push(text)
    },
  })

  const first = controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'first' }))
  const second = controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'second' }))

  assert.equal(pendingSettings.length, 2)
  pendingSettings[1]({ autoSpeakA: true, autoSpeakB: false })
  await second
  pendingSettings[0]({ autoSpeakA: true, autoSpeakB: false })
  await first

  assert.deepEqual(spoken, ['second'])
})

test('AutoSpeakHandler invalid later face event still cancels stale pending speech', async () => {
  const spoken = []
  let resolveSettings
  const controller = createAutoSpeakController({
    getDeckTtsSettings: async () => new Promise(resolve => {
      resolveSettings = resolve
    }),
    isEnglish: () => true,
    speakText: async (text) => {
      spoken.push(text)
    },
  })

  const first = controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'first' }))
  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: '' }))
  resolveSettings({ autoSpeakA: true, autoSpeakB: false })
  await first

  assert.deepEqual(spoken, [])
})

test('AutoSpeakHandler in-flight settings request does not overwrite changed cache', async () => {
  const spoken = []
  const pendingSettings = []
  let settingsRequestCount = 0
  const controller = createAutoSpeakController({
    getDeckTtsSettings: async () => {
      settingsRequestCount += 1
      return new Promise(resolve => pendingSettings.push(resolve))
    },
    isEnglish: () => true,
    speakText: async (text) => {
      spoken.push(text)
    },
  })

  const first = controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'old face' }))
  assert.equal(pendingSettings.length, 1)
  controller.handleDeckSettingsChanged({
    detail: { deckId: 42, settings: { autoSpeakA: false, autoSpeakB: false } },
  })
  pendingSettings[0]({ autoSpeakA: true, autoSpeakB: false })
  await first
  await controller.handlePracticeFaceShown(faceEvent({ deckId: 42, side: 'a', text: 'next face' }))

  assert.deepEqual(spoken, [])
  assert.equal(settingsRequestCount, 1)
})

function faceEvent(detail) {
  return { detail }
}
