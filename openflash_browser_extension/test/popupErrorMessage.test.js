import assert from 'node:assert/strict'
import test from 'node:test'
import {
  resolvePopupErrorMessage,
  resolveSessionErrorMessage,
} from '../src/popupErrorMessage.js'

test('ordinary 40101 error keeps its message', () => {
  assert.equal(
    resolvePopupErrorMessage({ code: 40101, message: 'Please log in first' }, 'Fallback'),
    'Please log in first',
  )
})

test('session 40101 error is silent', () => {
  assert.equal(
    resolveSessionErrorMessage({ code: 40101, message: 'Please log in first' }, 'Fallback'),
    '',
  )
})

test('non-40101 session error keeps its message', () => {
  assert.equal(
    resolveSessionErrorMessage({ code: 50301, message: 'Feature unavailable' }, 'Fallback'),
    'Feature unavailable',
  )
})
