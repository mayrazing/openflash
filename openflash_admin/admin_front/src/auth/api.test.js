import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { afterEach, test } from 'node:test'
import { request } from '../api/request.js'
import { getCurrentAdmin, loginAdmin, logoutAdmin } from './api.js'

const originalFetch = globalThis.fetch
const loginSource = await readFile(new URL('./AdminLogin.jsx', import.meta.url), 'utf8')

afterEach(() => {
  globalThis.fetch = originalFetch
})

function jsonResponse(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(payload),
  }
}

test('request includes the admin session cookie and returns response data', async () => {
  let captured
  globalThis.fetch = async (path, options) => {
    captured = { path, options }
    return jsonResponse(200, { code: 200, data: { username: 'root' } })
  }

  const result = await request('/api/admin/auth/me')

  assert.equal(captured.path, '/api/admin/auth/me')
  assert.equal(captured.options.credentials, 'include')
  assert.equal(captured.options.headers['Content-Type'], 'application/json')
  assert.deepEqual(result, { username: 'root' })
})

test('request reports 401 without exposing server response details', async () => {
  globalThis.fetch = async () => jsonResponse(401, {
    code: 40101,
    detail: 'database-password-must-not-escape',
  })

  await assert.rejects(
    request('/api/admin/auth/me'),
    error => error.status === 401
      && error.code === 40101
      && !error.message.includes('database-password-must-not-escape'),
  )
})

test('request handles a non-JSON 403 response safely', async () => {
  globalThis.fetch = async () => ({
    ok: false,
    status: 403,
    text: async () => '<html>proxy error</html>',
  })

  await assert.rejects(
    request('/api/admin/users'),
    error => error.status === 403 && error.code === null,
  )
})

test('HTTP 400 with business code 40002 displays invalid credentials', async () => {
  globalThis.fetch = async () => jsonResponse(400, { code: 40002, data: null })

  await assert.rejects(
    loginAdmin('root', 'wrong'),
    error => error.status === 400 && error.code === 40002,
  )
  assert.match(loginSource, /error\.code\s*===\s*40002/)
  assert.match(loginSource, /setErrorKey\('errors\.invalidCredentials'\)/)
})

test('HTTP 429 with business code 42902 displays login rate limit', async () => {
  globalThis.fetch = async () => jsonResponse(429, { code: 42902, data: null })

  await assert.rejects(
    loginAdmin('root', 'wrong'),
    error => error.status === 429 && error.code === 42902,
  )
  assert.match(loginSource, /error\.code\s*===\s*42902/)
  assert.match(loginSource, /setErrorKey\('errors\.loginRateLimited'\)/)
})

test('auth API uses the exact admin session endpoints', async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return jsonResponse(200, { code: 200, data: null })
  }

  await loginAdmin('root', 'secret')
  await getCurrentAdmin()
  await logoutAdmin()

  assert.deepEqual(calls.map(call => call.path), [
    '/api/admin/auth/login',
    '/api/admin/auth/me',
    '/api/admin/auth/logout',
  ])
  assert.equal(calls[0].options.method, 'POST')
  assert.equal(calls[0].options.body, JSON.stringify({ username: 'root', password: 'secret' }))
  assert.equal(calls[2].options.method, 'POST')
})
