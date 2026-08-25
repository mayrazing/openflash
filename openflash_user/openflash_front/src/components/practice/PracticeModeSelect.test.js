import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./PracticeModeSelect.jsx', import.meta.url), 'utf8')

test('practice mode choice buttons keep their semantic colors', () => {
  assert.doesNotMatch(source, /!text-white/)
})
