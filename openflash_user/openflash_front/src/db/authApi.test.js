import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { changePassword } from './database.js'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test('changePassword sends current and new password to the authenticated endpoint', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/auth/password')
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'include')
    assert.deepEqual(JSON.parse(options.body), {
      currentPassword: '1234',
      newPassword: 'new-secure-password',
    })
    return response({ code: 200, data: null })
  }

  await changePassword('1234', 'new-secure-password')
})

function response(payload) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(payload),
  }
}
