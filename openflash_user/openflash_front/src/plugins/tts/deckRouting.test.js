import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ttsDir = dirname(fileURLToPath(import.meta.url))
const srcDir = join(ttsDir, '..', '..')

test('manual card speech sends its deck id so the backend can use the deck default model', () => {
  const speakButton = readFileSync(join(ttsDir, 'SpeakButton.jsx'), 'utf8')
  const cardItem = readFileSync(join(srcDir, 'components', 'CardItem.jsx'), 'utf8')
  const practiceCard = readFileSync(join(srcDir, 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(speakButton, /ttsApi\.speakText\(current\.text, \{ deckId: current\.deckId \}\)/)
  assert.match(cardItem, /props=\{\{[^}]*deckId[^}]*\}\}\s+deckId=\{deckId\}/s)
  assert.match(practiceCard, /props=\{\{[^}]*deckId[^}]*\}\}\s+deckId=\{deckId\}/s)
})

test('AI result dialog preserves deck routing for its manual speech action', () => {
  const cardItem = readFileSync(join(srcDir, 'components', 'CardItem.jsx'), 'utf8')
  const practiceCard = readFileSync(join(srcDir, 'components', 'PracticeCard.jsx'), 'utf8')
  const aiOpenAction = readFileSync(join(srcDir, 'plugins', 'ai-card', 'AiOpenAction.jsx'), 'utf8')
  const practiceAiOpenAction = readFileSync(join(srcDir, 'plugins', 'ai-card', 'PracticeAiOpenAction.jsx'), 'utf8')
  const aiCardManager = readFileSync(join(srcDir, 'plugins', 'ai-card', 'AiCardManager.jsx'), 'utf8')
  const aiCardDialog = readFileSync(join(srcDir, 'plugins', 'ai-card', 'AiCardDialog.jsx'), 'utf8')

  assert.match(cardItem, /card\.open-actions[\s\S]*?deckId[\s\S]*?\},\s*deckId\)/)
  assert.match(practiceCard, /practice\.card\.open-actions[\s\S]*?deckId[\s\S]*?\},\s*deckId\)/)
  assert.match(aiOpenAction, /detail:\s*\{[^}]*deckId[^}]*\}/s)
  assert.match(practiceAiOpenAction, /detail:\s*\{[^}]*deckId[^}]*\}/s)
  assert.match(aiCardManager, /<AiCardDialog[\s\S]*?deckId=\{aiDialog\.deckId\}/)
  assert.match(aiCardDialog, /props=\{\{[^}]*deckId[^}]*\}\}\s+deckId=\{deckId\}/s)
})
