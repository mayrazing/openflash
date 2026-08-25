import { buildApiUrl, request } from '../../db/database.js'
import { getErrorMessage } from '../../lib/errorMessages.js'
import i18n from '../../i18n.js'
import { normalizeTtsText, isEnglish } from './utils.js'
import { ttsAudioCache } from './audioCache.js'

export { normalizeTtsText, isEnglish }

export const TTS_PLUGIN_IDS = ['tts']
export const SUPPORTED_TTS_ENGINES = ['cosyvoice3', 'piper']
export const TTS_ERROR_EVENT = 'pick-word-tts-error'
export const TTS_DECK_SETTINGS_CHANGED_EVENT = 'tts:deck-settings-changed'
export const TTS_DECK_SETTINGS_CHANGED_EVENTS = [TTS_DECK_SETTINGS_CHANGED_EVENT]

export const DEFAULT_DECK_TTS_SETTINGS = {
  autoSpeakA: false,
  autoSpeakB: false,
  engine: 'cosyvoice3',
}

export function isTtsPlugin(pluginId) {
  return pluginId === 'tts'
}

export function anyTtsInstalled(installedIds) {
  return (Array.isArray(installedIds) ? installedIds : []).some(isTtsPlugin)
}

export function ttsDeckSettingsChangedEvent() {
  return TTS_DECK_SETTINGS_CHANGED_EVENT
}

let currentAudio = null
let currentObjectUrl = null
let playbackRequestVersion = 0
let currentPlaybackAbortController = null
const cacheWriteVersions = new Map()

function cleanupCurrentAudio() {
  const audio = currentAudio
  const url = currentObjectUrl
  currentAudio = null
  currentObjectUrl = null
  if (audio) {
    try { audio.pause() } catch { /* ignore */ }
    audio.removeAttribute('src')
    try { audio.load() } catch { /* ignore */ }
  }
  if (url) URL.revokeObjectURL(url)
}

function cancelPlayback() {
  playbackRequestVersion += 1
  currentPlaybackAbortController?.abort()
  currentPlaybackAbortController = null
  cleanupCurrentAudio()
}

function beginPlaybackRequest() {
  cancelPlayback()
  const controller = new AbortController()
  currentPlaybackAbortController = controller
  return { requestVersion: playbackRequestVersion, signal: controller.signal }
}

function getCacheWriteVersion(cacheKey) {
  return cacheWriteVersions.get(cacheKey) ?? 0
}

function invalidateOlderCacheWrites(cacheKey) {
  cacheWriteVersions.set(cacheKey, getCacheWriteVersion(cacheKey) + 1)
}

function notifyTtsError(message) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(TTS_ERROR_EVENT, { detail: { message } }))
  }
}

async function fetchWav(url, body, signal) {
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok) {
    const responseText = await response.text()
    let code
    try { code = JSON.parse(responseText)?.code } catch { /* ignore */ }
    const error = new Error(`TTS error ${code ?? response.status}`)
    error.code = code
    throw error
  }
  const contentType = response.headers.get('Content-Type')?.split(';')[0]?.trim()?.toLowerCase()
  if (contentType !== 'audio/wav' && contentType !== 'audio/x-wav') {
    throw new Error('Invalid TTS audio response')
  }
  return response.blob()
}

async function playBlob(blob, { cacheKey, audioCache, requestVersion } = {}) {
  if (requestVersion !== playbackRequestVersion) return false
  cleanupCurrentAudio()
  const objectUrl = URL.createObjectURL(blob)
  const audio = new Audio(objectUrl)
  currentObjectUrl = objectUrl
  currentAudio = audio
  audio.addEventListener('ended', () => {
    if (audio !== currentAudio) return
    cleanupCurrentAudio()
  }, { once: true })
  audio.addEventListener('error', () => {
    if (audio !== currentAudio) return
    cleanupCurrentAudio()
    if (cacheKey && audioCache) audioCache.remove(cacheKey).catch(() => {})
    notifyTtsError(i18n.t('aiToast.ttsError'))
  }, { once: true })
  try {
    await audio.play()
    return true
  } catch (error) {
    if (audio === currentAudio) cleanupCurrentAudio()
    throw error
  }
}

export function createTtsApi({ pluginId = 'tts' } = {}) {
  if (!isTtsPlugin(pluginId)) {
    throw new Error(`Unsupported TTS plugin: ${pluginId}`)
  }

  const deckSettingsUrl = `/api/plugins/${pluginId}/decks`
  const defaultAudioUrl = buildApiUrl('/api/tts')

  async function getDeckTtsSettings(deckId) {
    const settings = await request(`${deckSettingsUrl}/${deckId}/settings`)
    return settings
      ? { ...DEFAULT_DECK_TTS_SETTINGS, ...settings }
      : { ...DEFAULT_DECK_TTS_SETTINGS }
  }

  async function saveDeckTtsSettings(deckId, settingsPayload) {
    const settings = await request(`${deckSettingsUrl}/${deckId}/settings`, {
      method: 'PUT',
      body: JSON.stringify(settingsPayload),
    })
    return settings
      ? { ...DEFAULT_DECK_TTS_SETTINGS, ...settings }
      : { ...DEFAULT_DECK_TTS_SETTINGS, ...settingsPayload }
  }

  async function getTtsEngineOptions() {
    const engines = await request('/api/plugins/tts/engines')
    const supported = Array.isArray(engines) ? engines : []
    return [...new Set(supported.filter(engine => SUPPORTED_TTS_ENGINES.includes(engine)))]
  }

  function dispatchDeckTtsSettingsChanged(deckId, settings) {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(TTS_DECK_SETTINGS_CHANGED_EVENT, {
        detail: { deckId, settings },
      }))
    }
  }

  async function speakText(text, options = {}) {
    const { requestVersion, signal } = beginPlaybackRequest()
    const normalized = normalizeTtsText(text)
    const cacheWriteVersion = getCacheWriteVersion(normalized)
    const audioCache = options.audioCache ?? ttsAudioCache
    let blob = null
    try {
      try {
        blob = await audioCache.get(normalized)
      } catch {
        // IndexedDB 不可用时仍允许本次发音。
      }
      if (!blob) {
        blob = await fetchWav(defaultAudioUrl, {
          deckId: options.deckId,
          text: normalized,
        }, signal)
        if (cacheWriteVersion === getCacheWriteVersion(normalized)) {
          try {
            await audioCache.put(normalized, blob)
          } catch {
            // 缓存失败不影响当前播放。
          }
        }
      }
      await playBlob(blob, { cacheKey: normalized, audioCache, requestVersion })
    } catch (error) {
      if (error?.name === 'AbortError') throw error
      if (requestVersion !== playbackRequestVersion) throw error
      const message = getErrorMessage(error?.code)
      options.onError?.(message)
      notifyTtsError(message)
      throw error
    }
  }

  async function previewText(text, engine, options = {}) {
    if (!SUPPORTED_TTS_ENGINES.includes(engine)) {
      throw new Error(`Unsupported TTS engine: ${engine}`)
    }
    const { requestVersion, signal } = beginPlaybackRequest()
    const normalized = normalizeTtsText(text)
    try {
      const blob = options.candidateBlob instanceof Blob
        ? options.candidateBlob
        : await fetchWav(buildApiUrl(`/api/tts/${engine}`), { text: normalized }, signal)
      const played = await playBlob(blob, { requestVersion })
      return played ? { engine, blob } : null
    } catch (error) {
      if (error?.name === 'AbortError') throw error
      if (requestVersion !== playbackRequestVersion) throw error
      const message = getErrorMessage(error?.code)
      options.onError?.(message)
      notifyTtsError(message)
      throw error
    }
  }

  async function replaceCachedAudio(text, blob, options = {}) {
    if (!(blob instanceof Blob)) return false
    const audioCache = options.audioCache ?? ttsAudioCache
    const cacheKey = normalizeTtsText(text)
    invalidateOlderCacheWrites(cacheKey)
    await audioCache.put(cacheKey, blob)
    return true
  }

  return {
    pluginId,
    TTS_ERROR_EVENT,
    TTS_DECK_SETTINGS_CHANGED_EVENT,
    getDeckTtsSettings,
    saveDeckTtsSettings,
    getTtsEngineOptions,
    dispatchDeckTtsSettingsChanged,
    speakText,
    previewText,
    replaceCachedAudio,
    cancelPlayback,
  }
}

export const ttsApi = createTtsApi()

export async function readAnyTtsAutoSpeak(deckId, installedIds = TTS_PLUGIN_IDS) {
  if (!anyTtsInstalled(installedIds)) {
    return { autoSpeakA: false, autoSpeakB: false }
  }
  try {
    const settings = await ttsApi.getDeckTtsSettings(deckId)
    return {
      autoSpeakA: settings.autoSpeakA === true,
      autoSpeakB: settings.autoSpeakB === true,
    }
  } catch {
    return { autoSpeakA: false, autoSpeakB: false }
  }
}
