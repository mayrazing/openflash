import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import * as usersApi from './api.js'

const {
  deleteUser,
  listUsers,
  updateUserBanned,
  updateUserCliAccess,
  updateUserRole,
} = usersApi

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function successResponse(data = null) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ code: 200, data }),
  }
}

test('listUsers sends the exact encoded search query', async () => {
  let captured
  globalThis.fetch = async (path, options) => {
    captured = { path, options }
    return successResponse({ clis: [], users: [{
      id: 7, username: 'amy', nickname: 'Amy', role: 'USER', cliAccess: {},
    }] })
  }

  const users = await listUsers('Amy & 管理员/one')

  assert.equal(captured.path, '/api/admin/users?query=Amy%20%26%20%E7%AE%A1%E7%90%86%E5%91%98%2Fone')
  assert.equal(captured.options.credentials, 'include')
  assert.equal(captured.options.method, undefined)
  assert.equal(users.users[0].username, 'amy')
})

test('updateUserRole encodes the id and sends only the exact role payload', async () => {
  let captured
  globalThis.fetch = async (path, options) => {
    captured = { path, options }
    return successResponse()
  }

  await updateUserRole('user/42', 'ADMIN')

  assert.equal(captured.path, '/api/admin/users/user%2F42/role')
  assert.equal(captured.options.method, 'PUT')
  assert.equal(captured.options.body, JSON.stringify({ role: 'ADMIN' }))
})

test('updateUserCliAccess encodes the id and CLI key and sends only enabled', async () => {
  let captured
  globalThis.fetch = async (path, options) => {
    captured = { path, options }
    return successResponse()
  }

  await updateUserCliAccess('user?42', 'future/cli', true)

  assert.equal(captured.path, '/api/admin/users/user%3F42/cli-access/future%2Fcli')
  assert.equal(captured.options.method, 'PUT')
  assert.equal(captured.options.body, JSON.stringify({ enabled: true }))
  assert.deepEqual(Object.keys(JSON.parse(captured.options.body)), ['enabled'])
})

test('updateUserOfferingAccess uses platform offering override endpoint', async () => {
  assert.equal(typeof usersApi.updateUserOfferingAccess, 'function')
  let captured
  globalThis.fetch = async (path, options) => {
    captured = { path, options }
    return successResponse()
  }

  await usersApi.updateUserOfferingAccess('user/42', 'offering/one', true)

  assert.equal(captured.path, '/api/admin/platform-ai/offerings/offering%2Fone/access/users/user%2F42')
  assert.equal(captured.options.method, 'PUT')
  assert.equal(captured.options.body, JSON.stringify({ enabled: true }))
})

test('ban and delete use fixed user account endpoints', async () => {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return successResponse()
  }

  await updateUserBanned('user/42', true)
  await deleteUser('user/42')

  assert.equal(calls[0].path, '/api/admin/users/user%2F42/banned')
  assert.equal(calls[0].options.method, 'PUT')
  assert.equal(calls[0].options.body, JSON.stringify({ banned: true }))
  assert.deepEqual(Object.keys(JSON.parse(calls[0].options.body)), ['banned'])
  assert.equal(calls[1].path, '/api/admin/users/user%2F42')
  assert.equal(calls[1].options.method, 'DELETE')
  assert.equal(calls[1].options.body, undefined)
})
