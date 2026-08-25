import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import assert from 'node:assert/strict'

const source = readFileSync(new URL('./DeckSettings.jsx', import.meta.url), 'utf8')

test('DeckSettings disables editable controls until settings are loaded', () => {
  assert.match(source, /const\s+settingsControlsDisabled\s*=\s*!loaded/)
  assert.match(source, /disabled=\{settingsControlsDisabled\}/)
})

test('DeckSettings loads main settings even when profile options fail', () => {
  assert.doesNotMatch(source, /Promise\.all\(\[getDeckSettings\(id\),\s*getReviewLoadProfiles\(\)\]\)/)
  assert.match(source, /getDeckSettings\(id\)\.then/)
  assert.match(source, /getReviewLoadProfiles\(\)[\s\S]*catch/)
})

test('DeckSettings builds save payload from named fields', () => {
  assert.doesNotMatch(source, /function\s+toPayload\s*\(\s*ncp\s*,\s*ret\s*,\s*rlp\s*,/)
  assert.match(source, /function\s+toPayload\s*\(\s*\{/)
  assert.match(source, /toPayload\(\{\s*newCardsPerDay/)
  assert.doesNotMatch(source, /function\s+toPayload\s*\(\s*\{[\s\S]{0,180}cardSortOrder/)
  assert.doesNotMatch(source, /return\s*\{[\s\S]{0,180}cardSortOrder/)
})

test('DeckSettings shows card sort above daily new card limit with segmented options', () => {
  assert.match(source, /t\('deckSettings\.cardSortOrder'\)[\s\S]*t\('deckSettings\.newCardsPerDay'\)/)
  assert.match(source, /options=\{\[\s*\{\s*value: 'created_desc'[\s\S]*value: 'created_asc'/)
  assert.match(source, /value=\{cardSortOrder\}/)
  assert.match(source, /onChange=\{setCardSortOrder\}/)
  assert.match(source, /getDeckCardSortOrder\(id\)/)
  assert.match(source, /saveDeckCardSortOrder\(id, cardSortOrder\)/)
})

test('DeckSettings save notice names the saved section and clears when any save fails', () => {
  assert.match(source, /const\s+\[savedMessage,\s*setSavedMessage\]\s*=\s*useState\(''\)/)
  assert.match(source, /function\s+showSavedNotice\s*\(\s*message\s*\)/)
  assert.match(source, /setSavedMessage\(message\)/)
  assert.match(source, /setSavedMessage\(''\)/)
  assert.doesNotMatch(source, /\{saved\s*&&/)
})

test('DeckSettings does not keep unused current settings refs', () => {
  assert.doesNotMatch(source, /currentSettingsRef/)
  assert.doesNotMatch(source, /currentAiSettingsRef/)
})

test('DeckSettings uses one queued save helper for main settings', () => {
  assert.match(source, /function\s+createQueuedSaver\s*\(/)
  assert.doesNotMatch(source, /async\s+function\s+persistSettings\s*\(/)
})

test('DeckSettings delegates plugin settings UI to plugin slot', () => {
  assert.match(source, /<PluginSlot\s+slotName="deck-settings\.sections"/)
  assert.match(source, /props=\{\{\s*deckId: id,\s*deckName\s*\}\}/)
})

test('DeckSettings does not own TTS auto speak fields', () => {
  assert.doesNotMatch(source, new RegExp('autoSpeak' + 'A'))
  assert.doesNotMatch(source, new RegExp('autoSpeak' + 'B'))
})

test('DeckSettings shows deck name in heading after loading', () => {
  assert.match(source, /getDeck.*import.*database|getDeck.*from.*database|import.*getDeck.*database/, 'getDeck must be imported from database')
  assert.match(source, /const\s+\[deckName,\s*setDeckName\]\s*=\s*useState\(''?\)/)
  assert.match(source, /getDeck\(id\)\.then[\s\S]*setDeckName/)
  assert.match(source, /t\('deckSettings\.title', \{ name: deckName \}\)/)
})
