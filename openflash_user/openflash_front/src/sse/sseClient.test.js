import test from 'node:test'
import assert from 'node:assert/strict'
import {
  beginAuthAttempt,
  subscribeSessionInvalidation,
} from '../auth/sessionInvalidation.js'
import { createSseClient, subscribeToAccountInvalidation } from './sseClient.js'

class FakeEventSource {
  static instances = []

  constructor(url, options) {
    this.url = url
    this.options = options
    this.listeners = new Map()
    FakeEventSource.instances.push(this)
  }

  addEventListener(name, listener) {
    this.listeners.set(name, listener)
  }

  removeEventListener(name, listener) {
    if (this.listeners.get(name) === listener) this.listeners.delete(name)
  }

  close() {
    this.closed = true
  }
}

test('core SSE client shares one connection across plugin subscriptions', () => {
  FakeEventSource.instances = []
  const client = createSseClient('/api/sse/notifications', FakeEventSource, () => {})
  const received = []

  const unsubscribe = client.subscribe('ai-cache-ready', event => received.push(event.data))
  const source = FakeEventSource.instances[0]
  source.listeners.get('ai-cache-ready')({ data: 'ready' })

  assert.equal(FakeEventSource.instances.length, 1)
  assert.deepEqual(source.options, { withCredentials: true })
  assert.deepEqual(received, ['ready'])

  unsubscribe()
  assert.equal(source.listeners.has('ai-cache-ready'), false)
  client.close()
  assert.equal(source.closed, true)
})

test('account-invalidated SSE publishes its backend reason through the shared channel', () => {
  FakeEventSource.instances = []
  beginAuthAttempt()
  const received = []
  const unsubscribeInvalidation = subscribeSessionInvalidation(event => received.push(event))
  const client = createSseClient('/api/sse/notifications', FakeEventSource, () => {})
  const unsubscribeSse = subscribeToAccountInvalidation(client)

  FakeEventSource.instances[0].listeners.get('account-invalidated')({
    data: JSON.stringify({ reason: 'DELETED', code: 40104 }),
  })

  assert.deepEqual(received, [{
    code: 40104,
    reason: 'DELETED',
    reasonKey: 'errors.40104',
  }])
  unsubscribeSse()
  unsubscribeInvalidation()
  client.close()
  beginAuthAttempt()
})

test('malformed account-invalidated SSE payload is ignored', () => {
  FakeEventSource.instances = []
  beginAuthAttempt()
  const received = []
  const unsubscribeInvalidation = subscribeSessionInvalidation(event => received.push(event))
  const client = createSseClient('/api/sse/notifications', FakeEventSource, () => {})
  const unsubscribeSse = subscribeToAccountInvalidation(client)

  FakeEventSource.instances[0].listeners.get('account-invalidated')({ data: '{' })

  assert.deepEqual(received, [])
  unsubscribeSse()
  unsubscribeInvalidation()
  client.close()
  beginAuthAttempt()
})
