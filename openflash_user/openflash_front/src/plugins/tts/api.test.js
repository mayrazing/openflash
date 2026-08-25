import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  createTtsApi,
  DEFAULT_DECK_TTS_SETTINGS,
  isEnglish,
  isTtsPlugin,
  normalizeTtsText,
  readAnyTtsAutoSpeak,
  TTS_PLUGIN_IDS,
} from './api.js'

const originalFetch = globalThis.fetch
const originalWindow = globalThis.window
const originalCustomEvent = globalThis.CustomEvent
const originalAudio = globalThis.Audio
const originalCreateObjectUrl = globalThis.URL.createObjectURL
const originalRevokeObjectUrl = globalThis.URL.revokeObjectURL

afterEach(() => {
  globalThis.fetch = originalFetch
  globalThis.window = originalWindow
  globalThis.CustomEvent = originalCustomEvent
  globalThis.Audio = originalAudio
  globalThis.URL.createObjectURL = originalCreateObjectUrl
  globalThis.URL.revokeObjectURL = originalRevokeObjectUrl
})

function unifiedApi() {
  return createTtsApi({ pluginId: 'tts' })
}

test('normalizeTtsText trims whitespace and expands slashes', () => {
  assert.equal(normalizeTtsText(' apple / pear '), 'apple or pear')
})

test('isEnglish accepts ascii words and rejects non-English letters', () => {
  assert.equal(isEnglish('apple 123'), true)
  assert.equal(isEnglish('omena'), true)
  assert.equal(isEnglish('éclair'), false)
  assert.equal(isEnglish('苹果'), false)
})

test('isTtsPlugin recognizes only the unified TTS plugin id', () => {
  assert.deepEqual(TTS_PLUGIN_IDS, ['tts'])
  assert.equal(isTtsPlugin('tts'), true)
  assert.equal(isTtsPlugin('tts-cosyvoice3'), false)
  assert.equal(isTtsPlugin('tts-piper'), false)
})

test('getDeckTtsSettings reads unified deck settings and fills defaults', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/plugins/tts/decks/42/settings')
    assert.equal(options.credentials, 'include')
    return response({ code: 200, data: { autoSpeakA: true, engine: 'piper' } })
  }

  const settings = await unifiedApi().getDeckTtsSettings(42)

  assert.deepEqual(settings, {
    ...DEFAULT_DECK_TTS_SETTINGS,
    autoSpeakA: true,
    engine: 'piper',
  })
})

test('getTtsEngineOptions returns only supported DB-registered models', async () => {
  globalThis.fetch = async (url) => {
    assert.equal(url, '/api/plugins/tts/engines')
    return response({ code: 200, data: ['piper', 'unsupported', 'cosyvoice3', 'piper'] })
  }

  assert.deepEqual(await unifiedApi().getTtsEngineOptions(), ['piper', 'cosyvoice3'])
})

test('saveDeckTtsSettings sends auto-speak flags and default model', async () => {
  const settingsPayload = { autoSpeakA: true, autoSpeakB: false, engine: 'piper' }
  let capturedBody = null
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/plugins/tts/decks/42/settings')
    assert.equal(options.method, 'PUT')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: settingsPayload })
  }

  const saved = await unifiedApi().saveDeckTtsSettings(42, settingsPayload)

  assert.deepEqual(capturedBody, settingsPayload)
  assert.deepEqual(saved, settingsPayload)
})

test('dispatchDeckTtsSettingsChanged publishes one unified event', () => {
  const events = []
  globalThis.CustomEvent = class {
    constructor(type, options) {
      this.type = type
      this.detail = options?.detail
    }
  }
  globalThis.window = {
    dispatchEvent(event) {
      events.push(event)
    },
  }

  const settingsPayload = { autoSpeakA: true, autoSpeakB: false, engine: 'piper' }
  unifiedApi().dispatchDeckTtsSettingsChanged(42, settingsPayload)

  assert.equal(events[0].type, 'tts:deck-settings-changed')
  assert.deepEqual(events[0].detail, { deckId: 42, settings: settingsPayload })
})

test('speakText uses deck default model endpoint and caches only by normalized text', async () => {
  let cachedKey = null
  let cachedBlob = null
  const audioCache = {
    async get(key) {
      assert.equal(key, 'private card text')
      return null
    },
    async put(key, blob) {
      cachedKey = key
      cachedBlob = blob
    },
    async remove() {},
  }
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/tts')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), { deckId: 42, text: 'private card text' })
    return wavResponse('default-wav')
  }
  installAudioDoubles()

  await unifiedApi().speakText('private card text', { deckId: 42, audioCache })

  assert.equal(cachedKey, 'private card text')
  assert.equal(await cachedBlob.text(), 'default-wav')
})

test('speakText reuses one user-confirmed cache across default model changes', async () => {
  const entries = new Map()
  const audioCache = memoryAudioCache(entries)
  let fetchCount = 0
  globalThis.fetch = async () => {
    fetchCount++
    return wavResponse('generated-once')
  }
  installAudioDoubles()

  const api = unifiedApi()
  await api.speakText(' shared text ', { deckId: 42, audioCache })
  await api.speakText('shared text', { deckId: 99, audioCache })

  assert.equal(fetchCount, 1)
  assert.deepEqual([...entries.keys()], ['shared text'])
})

test('previewText plays selected model output without reading or writing persistent cache', async () => {
  const cacheCalls = []
  const audioCache = {
    async get(key) { cacheCalls.push(['get', key]) },
    async put(key) { cacheCalls.push(['put', key]) },
    async remove(key) { cacheCalls.push(['remove', key]) },
  }
  const previewBlob = new Blob(['piper-preview'], { type: 'audio/wav' })
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/tts/piper')
    assert.deepEqual(JSON.parse(options.body), { text: 'difficult' })
    return {
      ok: true,
      headers: { get: () => 'audio/wav' },
      blob: async () => previewBlob,
    }
  }
  installAudioDoubles()

  const candidate = await unifiedApi().previewText('difficult', 'piper', { audioCache })

  assert.deepEqual(candidate, { engine: 'piper', blob: previewBlob })
  assert.deepEqual(cacheCalls, [])
})

test('previewText replays a retained candidate without generating or changing persistent cache', async () => {
  const cacheCalls = []
  const retainedBlob = new Blob(['retained-preview'], { type: 'audio/wav' })
  let fetchCount = 0
  globalThis.fetch = async () => {
    fetchCount++
    return wavResponse('unexpected-regeneration')
  }
  installAudioDoubles()

  const candidate = await unifiedApi().previewText('difficult', 'piper', {
    candidateBlob: retainedBlob,
    audioCache: {
      async get(key) { cacheCalls.push(['get', key]) },
      async put(key) { cacheCalls.push(['put', key]) },
      async remove(key) { cacheCalls.push(['remove', key]) },
    },
  })

  assert.equal(fetchCount, 0)
  assert.equal(candidate.engine, 'piper')
  assert.equal(candidate.blob, retainedBlob)
  assert.deepEqual(cacheCalls, [])
})

test('a slower obsolete model preview cannot play over the latest request', async () => {
  const pending = new Map()
  const cosyBlob = new Blob(['cosy'], { type: 'audio/wav' })
  const piperBlob = new Blob(['piper'], { type: 'audio/wav' })
  const played = []
  globalThis.fetch = url => new Promise(resolve => pending.set(url, resolve))
  globalThis.URL.createObjectURL = blob => blob === cosyBlob ? 'blob:cosy' : 'blob:piper'
  globalThis.URL.revokeObjectURL = () => {}
  globalThis.Audio = class {
    constructor(url) { this.url = url }
    addEventListener() {}
    async play() { played.push(this.url) }
    pause() {}
    removeAttribute() {}
    load() {}
  }

  const api = unifiedApi()
  const cosyRequest = api.previewText('difficult', 'cosyvoice3')
  const piperRequest = api.previewText('difficult', 'piper')
  pending.get('/api/tts/piper')(wavBlobResponse(piperBlob))
  await piperRequest
  pending.get('/api/tts/cosyvoice3')(wavBlobResponse(cosyBlob))
  const obsoleteCandidate = await cosyRequest

  assert.deepEqual(played, ['blob:piper'])
  assert.equal(obsoleteCandidate, null)
})

test('confirming a candidate prevents an older normal request from overwriting its cache', async () => {
  let resolveNormalRequest
  const oldGeneratedBlob = new Blob(['old-generated'], { type: 'audio/wav' })
  const selectedBlob = new Blob(['user-selected'], { type: 'audio/wav' })
  const entries = new Map()
  const audioCache = memoryAudioCache(entries)
  globalThis.fetch = () => new Promise(resolve => {
    resolveNormalRequest = resolve
  })
  installAudioDoubles()

  const api = unifiedApi()
  const normalRequest = api.speakText('difficult', { deckId: 42, audioCache })
  await api.replaceCachedAudio('difficult', selectedBlob, { audioCache })
  resolveNormalRequest(wavBlobResponse(oldGeneratedBlob))
  await normalRequest

  assert.equal(entries.get('difficult'), selectedBlob)
})

test('cancelling playback prevents a pending preview from playing or becoming selectable', async () => {
  let resolvePreview
  const previewBlob = new Blob(['late-preview'], { type: 'audio/wav' })
  const played = []
  globalThis.fetch = () => new Promise(resolve => {
    resolvePreview = resolve
  })
  globalThis.URL.createObjectURL = () => 'blob:late-preview'
  globalThis.URL.revokeObjectURL = () => {}
  globalThis.Audio = class {
    addEventListener() {}
    async play() { played.push('late-preview') }
    pause() {}
    removeAttribute() {}
    load() {}
  }

  const api = unifiedApi()
  const pendingPreview = api.previewText('difficult', 'piper')
  api.cancelPlayback()
  resolvePreview(wavBlobResponse(previewBlob))

  assert.equal(await pendingPreview, null)
  assert.deepEqual(played, [])
})

test('replaceCachedAudio overwrites the exact normalized-text cache entry', async () => {
  const oldBlob = new Blob(['wrong'], { type: 'audio/wav' })
  const selectedBlob = new Blob(['correct'], { type: 'audio/wav' })
  const entries = new Map([['difficult', oldBlob]])
  const audioCache = memoryAudioCache(entries)

  const replaced = await unifiedApi().replaceCachedAudio(' difficult ', selectedBlob, { audioCache })

  assert.equal(replaced, true)
  assert.equal(entries.get('difficult'), selectedBlob)
  assert.equal(await entries.get('difficult').text(), 'correct')
})

test('readAnyTtsAutoSpeak reads one unified TTS setting only when installed', async () => {
  let requestCount = 0
  globalThis.fetch = async (url) => {
    requestCount++
    assert.equal(url, '/api/plugins/tts/decks/42/settings')
    return response({ code: 200, data: { autoSpeakA: true, autoSpeakB: false } })
  }

  assert.deepEqual(await readAnyTtsAutoSpeak(42, ['tts']), {
    autoSpeakA: true,
    autoSpeakB: false,
  })
  assert.deepEqual(await readAnyTtsAutoSpeak(42, []), {
    autoSpeakA: false,
    autoSpeakB: false,
  })
  assert.equal(requestCount, 1)
})

function installAudioDoubles() {
  globalThis.URL.createObjectURL = () => 'blob:tts-test'
  globalThis.URL.revokeObjectURL = () => {}
  globalThis.Audio = class {
    addEventListener() {}
    async play() {}
    pause() {}
    removeAttribute() {}
    load() {}
  }
}

function memoryAudioCache(entries) {
  return {
    async get(key) {
      return entries.get(key) ?? null
    },
    async put(key, blob) {
      entries.set(key, blob)
      return true
    },
    async remove(key) {
      entries.delete(key)
      return true
    },
  }
}

function wavResponse(contents) {
  return {
    ok: true,
    headers: { get: () => 'audio/wav' },
    blob: async () => new Blob([contents], { type: 'audio/wav' }),
  }
}

function wavBlobResponse(blob) {
  return {
    ok: true,
    headers: { get: () => 'audio/wav' },
    blob: async () => blob,
  }
}

function response(body) {
  return {
    status: 200,
    ok: true,
    text: async () => JSON.stringify(body),
  }
}
