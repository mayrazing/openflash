import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('TTS errors render with the Konsta toast component', () => {
  const source = readFileSync(new URL('./TtsToast.jsx', import.meta.url), 'utf8')

  assert.match(source, /Toast.*from 'konsta\/react'/)
  assert.match(source, /<Toast/)
})
