import { afterEach, beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { UnauthorizedError } from '../../db/database.js'
import {
  DEFAULT_DECK_AI_SETTINGS,
  buildAiCacheRegeneratePath,
  buildAiCacheStatusPath,
  getAiFeatureState,
  getDeckAiSettings,
  regenerateAiCache,
  saveDeckAiSettings,
} from './api.js'

const originalFetch = globalThis.fetch

beforeEach(() => {
  globalThis.fetch = async () => {
    throw new Error('fetch mock not configured')
  }
})

afterEach(() => {
  globalThis.fetch = originalFetch
})

test('ai-card API owns card and deck defaults', () => {
  assert.deepEqual(DEFAULT_DECK_AI_SETTINGS, {
    aiExplanationEnabledA: false,
    aiExplanationEnabledB: false,
    aiExplanationPromptA: null,
    aiExplanationPromptB: null,
    aiCompletionEnabled: false,
    aiCompletionPrompt: null,
  })
})

test('buildAiCacheStatusPath includes encoded side when present', () => {
  assert.equal(
    buildAiCacheStatusPath(7, 'a/b'),
    '/api/cards/7/ai-cache-status?side=a%2Fb'
  )
})

test('buildAiCacheStatusPath omits side when absent', () => {
  assert.equal(buildAiCacheStatusPath(7), '/api/cards/7/ai-cache-status')
})

test('buildAiCacheRegeneratePath includes encoded side when present', () => {
  assert.equal(
    buildAiCacheRegeneratePath(7, 'a/b'),
    '/api/cards/7/ai-cache-regenerate?side=a%2Fb'
  )
})

test('regenerateAiCache posts regenerate endpoint', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/cards/7/ai-cache-regenerate?side=B')
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'include')
    return response({ code: 200, data: { status: 'queued' } })
  }

  assert.deepEqual(await regenerateAiCache(7, 'B'), { status: 'queued' })
})

test('getAiFeatureState normalizes side completion flag', async () => {
  globalThis.fetch = async (url) => {
    assert.equal(url, '/api/plugins/ai-card/features')
    return response({ code: 200, data: { sideCompletionEnabled: true } })
  }

  assert.deepEqual(await getAiFeatureState(), { sideCompletionEnabled: true })
})

test('getDeckAiSettings uses deck ai settings endpoint and merges defaults', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/decks/42/ai-settings')
    assert.equal(options.credentials, 'include')
    return response({
      code: 200,
      data: {
        aiExplanationEnabledA: false,
        aiExplanationEnabledB: true,
        aiExplanationPromptA: 'Explain A',
        aiExplanationPromptB: 'Explain B',
        aiCompletionEnabled: false,
        aiCompletionPrompt: 'Complete',
      },
    })
  }

  const settings = await getDeckAiSettings(42)

  assert.deepEqual(settings, {
    ...DEFAULT_DECK_AI_SETTINGS,
    aiExplanationEnabledA: false,
    aiExplanationEnabledB: true,
    aiExplanationPromptA: 'Explain A',
    aiExplanationPromptB: 'Explain B',
    aiCompletionEnabled: false,
    aiCompletionPrompt: 'Complete',
  })
})

test('getDeckAiSettings fills missing fields with defaults', async () => {
  globalThis.fetch = async () => response({
    code: 200,
    data: {
      aiExplanationEnabledA: false,
    },
  })

  const settings = await getDeckAiSettings(7)

  assert.equal(settings.aiExplanationEnabledA, false)
  assert.equal(settings.aiExplanationEnabledB, false)
  assert.equal(settings.aiExplanationPromptA, null)
  assert.equal(settings.aiExplanationPromptB, null)
  assert.equal(settings.aiCompletionEnabled, false)
  assert.equal(settings.aiCompletionPrompt, null)
})

test('saveDeckAiSettings sends PUT JSON body and merges returned data', async () => {
  const settingsPayload = {
    aiExplanationEnabledA: false,
    aiExplanationEnabledB: true,
    aiExplanationPromptA: 'Explain A',
    aiExplanationPromptB: 'Explain B',
    aiCompletionEnabled: true,
    aiCompletionPrompt: 'Complete this card',
  }
  let capturedBody = null

  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/decks/42/ai-settings')
    assert.equal(options.method, 'PUT')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['Content-Type'], 'application/json')
    capturedBody = JSON.parse(options.body)
    return response({
      code: 200,
      data: {
        aiExplanationEnabledA: false,
        aiExplanationEnabledB: false,
        aiExplanationPromptA: 'Saved A',
        aiExplanationPromptB: 'Saved B',
        aiCompletionEnabled: true,
        aiCompletionPrompt: 'Saved prompt',
      },
    })
  }

  const saved = await saveDeckAiSettings(42, settingsPayload)

  assert.deepEqual(capturedBody, settingsPayload)
  assert.deepEqual(saved, {
    ...DEFAULT_DECK_AI_SETTINGS,
    aiExplanationEnabledA: false,
    aiExplanationEnabledB: false,
    aiExplanationPromptA: 'Saved A',
    aiExplanationPromptB: 'Saved B',
    aiCompletionEnabled: true,
    aiCompletionPrompt: 'Saved prompt',
  })
})

test('saveDeckAiSettings falls back to submitted payload when response data is null', async () => {
  const settingsPayload = {
    aiExplanationEnabledA: false,
    aiExplanationEnabledB: true,
    aiExplanationPromptA: 'Local A',
    aiExplanationPromptB: 'Local B',
    aiCompletionEnabled: true,
    aiCompletionPrompt: 'Local prompt',
  }
  globalThis.fetch = async () => response({ code: 200, data: null })

  const saved = await saveDeckAiSettings(42, settingsPayload)

  assert.deepEqual(saved, {
    ...DEFAULT_DECK_AI_SETTINGS,
    ...settingsPayload,
  })
})

test('getDeckAiSettings throws err.code when api code is not 200', async () => {
  globalThis.fetch = async () => response({ code: 409, data: null })

  await assert.rejects(
    () => getDeckAiSettings(42),
    (err) => {
      assert.equal(err.code, 409)
      return true
    },
  )
})

test('saveDeckAiSettings throws err.code when api code is not 200', async () => {
  globalThis.fetch = async () => response({ code: 422, data: null })

  await assert.rejects(
    () => saveDeckAiSettings(42, {
      aiExplanationEnabledA: false,
      aiExplanationEnabledB: true,
      aiExplanationPromptA: null,
      aiExplanationPromptB: null,
      aiCompletionEnabled: true,
      aiCompletionPrompt: null,
    }),
    (err) => {
      assert.equal(err.code, 422)
      return true
    },
  )
})

test('getDeckAiSettings throws UnauthorizedError on 401', async () => {
  globalThis.fetch = async () => response({ code: 401, data: null }, { ok: false, status: 401 })

  await assert.rejects(
    () => getDeckAiSettings(42),
    (err) => {
      assert.equal(err instanceof UnauthorizedError, true)
      assert.equal(err.message, '请先登录')
      assert.equal(err.code, 401)
      return true
    },
  )
})

function response(payload, options = {}) {
  return {
    ok: options.ok ?? true,
    status: options.status ?? 200,
    async text() {
      return JSON.stringify(payload)
    },
  }
}
