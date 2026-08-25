import test from 'node:test'
import assert from 'node:assert/strict'
import { getErrorMessage, getKnownErrorMessage } from './errorMessages.js'

// i18n 初始语言固定为 en，所以默认返回英文文案。

test('returns english message for known code', () => {
  assert.equal(getErrorMessage(40010), 'Card already exists')
})

test('returns english message for missing user AI config', () => {
  assert.equal(getErrorMessage(40052), 'Please configure AI in Settings first')
})

test('returns actionable message when saved API key cannot be decrypted', () => {
  assert.equal(
    getErrorMessage(50010),
    'The saved API Key cannot be decrypted. Re-enter it and save the configuration',
  )
})

test('returns english AI upstream message', () => {
  assert.equal(getErrorMessage(50204), 'Cannot connect to AI service — check your configuration')
})

test('returns english Codex configuration and runtime messages', () => {
  assert.equal(getErrorMessage(40055), 'Model configuration is invalid or stale')
  assert.equal(getErrorMessage(40056), 'This AI provider is reserved by the system')
  assert.equal(getErrorMessage(50208), 'The model service is unavailable')
  assert.equal(getErrorMessage(50209), 'The model service is temporarily unavailable')
  assert.equal(getErrorMessage(50210), 'Model request failed')
})

test('returns default for unknown code', () => {
  assert.equal(getErrorMessage(99999), 'Operation failed — please retry')
})

test('returns default for undefined', () => {
  assert.equal(getErrorMessage(undefined), 'Operation failed — please retry')
})

test('returns message for SSE not supported', () => {
  assert.equal(getErrorMessage(60001), 'Your browser does not support SSE notifications')
})

test('returns message for SSE invalid payload', () => {
  assert.equal(getErrorMessage(60002), 'Invalid SSE message format')
})

test('getKnownErrorMessage returns null for null code', () => {
  assert.equal(getKnownErrorMessage(null), null)
})

test('getKnownErrorMessage returns null for unknown code', () => {
  assert.equal(getKnownErrorMessage(99999), null)
})

test('getKnownErrorMessage returns message for known code', () => {
  assert.equal(getKnownErrorMessage(40010), 'Card already exists')
})
