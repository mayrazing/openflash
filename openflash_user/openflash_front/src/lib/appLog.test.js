import test from 'node:test'
import assert from 'node:assert/strict'
import { mock } from 'node:test'
import { appError, appWarn } from './appLog.js'

test('appError calls console.error with code prefix', () => {
  const calls = []
  mock.method(console, 'error', (...args) => calls.push(args))
  appError(40010, '卡片操作失败')
  assert.equal(calls.length, 1)
  assert.ok(calls[0][0].includes('[E:40010]'), `expected [E:40010] in: ${calls[0][0]}`)
  mock.restoreAll()
})

test('appError includes original message', () => {
  const calls = []
  mock.method(console, 'error', (...args) => calls.push(args))
  appError(50000, '服务器错误')
  assert.ok(calls[0][0].includes('服务器错误'))
  mock.restoreAll()
})

test('appWarn calls console.warn with code prefix', () => {
  const calls = []
  mock.method(console, 'warn', (...args) => calls.push(args))
  appWarn(60001, '不支持 SSE')
  assert.equal(calls.length, 1)
  assert.ok(calls[0][0].includes('[E:60001]'))
  mock.restoreAll()
})

test('appError forwards extra args', () => {
  const calls = []
  mock.method(console, 'error', (...args) => calls.push(args))
  const err = new Error('cause')
  appError(50000, '失败', err)
  assert.equal(calls[0][1], err)
  mock.restoreAll()
})
