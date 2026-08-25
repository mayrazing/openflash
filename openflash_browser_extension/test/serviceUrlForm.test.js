import assert from 'node:assert/strict'
import test from 'node:test'
import { buildAuthUrl, persistServiceUrlValue } from '../src/serviceUrlForm.js'

test('persistServiceUrlValue normalizes and stores current input value', async () => {
  const saved = []

  const result = await persistServiceUrlValue(' http://localhost:5173/// ', async (value) => {
    saved.push(value)
  })

  assert.equal(result, 'http://localhost:5173')
  assert.deepEqual(saved, ['http://localhost:5173'])
})

test('buildAuthUrl uses current normalized service URL', () => {
  assert.equal(buildAuthUrl(' http://localhost:5173/// '), 'http://localhost:5173/auth')
})
