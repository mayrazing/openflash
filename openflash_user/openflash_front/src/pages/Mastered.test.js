import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

test('Mastered page shows source deck name for each mastered card', () => {
  const source = readFileSync(join(__dirname, 'Mastered.jsx'), 'utf8')

  assert.match(source, /card\.deckName/)
  assert.match(source, /t\('mastered\.fromDeck', \{ deckName: card\.deckName/)
})
