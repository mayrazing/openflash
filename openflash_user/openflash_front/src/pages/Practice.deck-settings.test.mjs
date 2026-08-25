import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import assert from 'node:assert/strict'

const files = [
  './Practice.jsx',
  '../hooks/usePracticeSession.js',
  '../hooks/usePracticePersistence.js',
  '../hooks/usePracticeBootstrap.js',
  '../hooks/usePracticeEngine.js',
]
const source = files.map(p => readFileSync(new URL(p, import.meta.url), 'utf8')).join('\n')

test('Practice no longer keeps legacy live deck settings reconciliation code', () => {
  assert.doesNotMatch(source, /pendingSettingsRef/)
  assert.doesNotMatch(source, /reconcileActiveSessionForSettings/)
})

test('Practice loads pending summary and today cards through one startup helper', () => {
  assert.match(source, /getPracticeStartupSnapshot/)
  assert.doesNotMatch(source, /getDynamicPendingPracticeSummary\(id,\s*s\.newCardsPerDay\)[\s\S]*getTodayCardsByDeck\(id,\s*s\.newCardsPerDay\)/)
})

test('Practice does not subscribe to global settings for deck-scoped behavior', () => {
  assert.doesNotMatch(source, /subscribeSettingsChanged/)
})

test('Practice has no leftover mutation-in-flight settings bridge state', () => {
  assert.doesNotMatch(source, /practiceMutationInFlightRef/)
})

test('Practice dispatches face shown events instead of owning TTS auto speak decisions', () => {
  assert.match(source, /practice:face-shown/)
  assert.match(source, /dispatchPracticeFaceShown/)
  assert.doesNotMatch(source, new RegExp('autoSpeak' + 'A'))
  assert.doesNotMatch(source, new RegExp('autoSpeak' + 'B'))
  assert.doesNotMatch(source, /speakText/)
})

test('Practice persists review load profile in saved sessions', () => {
  const persistenceSource = readFileSync(new URL('../hooks/usePracticePersistence.js', import.meta.url), 'utf8')
  assert.match(persistenceSource, /settingsReviewLoadProfile/)
  assert.match(persistenceSource, /next\.settingsReviewLoadProfile\s*\?\?\s*settingsRef\.current\.reviewLoadProfile/)
})
