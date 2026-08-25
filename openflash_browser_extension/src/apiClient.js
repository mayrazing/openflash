import { normalizeServiceUrl } from './storage.js'
import { t } from './i18n.js'

export function buildApiUrl(baseUrl, path) {
  return `${normalizeServiceUrl(baseUrl)}${path}`
}

export function getApiErrorMessage(code) {
  const key = `errors.${code}`
  const message = t(key)
  return message === key ? `API error ${code ?? 'unknown'}` : message
}

export function normalizeApiPayload(payload) {
  if (!payload || payload.code !== 200) {
    const error = new Error(getApiErrorMessage(payload?.code))
    error.code = payload?.code
    throw error
  }
  return payload.data ?? null
}

export async function requestJson(baseUrl, path, options = {}) {
  const response = await fetch(buildApiUrl(baseUrl, path), {
    signal: AbortSignal.timeout(15000),
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  })
  const text = await response.text()
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    // non-JSON response body — payload stays null
  }
  if (response.status === 401) {
    const error = new Error(getApiErrorMessage(payload?.code ?? 40101))
    error.code = payload?.code ?? 40101
    throw error
  }
  return normalizeApiPayload(payload)
}

export async function uploadImageFile(baseUrl, file) {
  const formData = new FormData()
  formData.append('file', file, 'image.jpg')
  const response = await fetch(buildApiUrl(baseUrl, '/api/upload'), {
    method: 'POST',
    signal: AbortSignal.timeout(15000),
    credentials: 'include',
    body: formData,
  })
  const payload = await response.json()
  const data = normalizeApiPayload(payload)
  return data?.url ?? null
}

export async function ensureBrowserImportEnabled(baseUrl) {
  await requestJson(baseUrl, '/api/browser-import/images/transfer', {
    method: 'POST',
    body: JSON.stringify({ urls: [] }),
  })
}

export const DEFAULT_DECK_AI_SETTINGS = {
  aiExplanationEnabledA: false,
  aiExplanationEnabledB: false,
  aiExplanationPromptA: null,
  aiExplanationPromptB: null,
  aiCompletionEnabled: false,
  aiCompletionPrompt: null,
}

export const api = {
  settings: (baseUrl) => requestJson(baseUrl, '/api/settings'),
  me: (baseUrl) => requestJson(baseUrl, '/api/auth/me'),
  logout: (baseUrl) => requestJson(baseUrl, '/api/auth/logout', { method: 'POST' }),
  decks: (baseUrl) => requestJson(baseUrl, '/api/decks'),
  createDeck: (baseUrl, name) => requestJson(baseUrl, '/api/decks', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }),
  deleteDeck: (baseUrl, deckId) => requestJson(baseUrl, `/api/decks/${deckId}`, { method: 'DELETE' }),
  getAiSettings: (baseUrl, deckId) => requestJson(baseUrl, `/api/decks/${deckId}/ai-settings`),
  saveAiSettings: (baseUrl, deckId, payload) => requestJson(baseUrl, `/api/decks/${deckId}/ai-settings`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  transferImages: (baseUrl, urls) => requestJson(baseUrl, '/api/browser-import/images/transfer', {
    method: 'POST',
    body: JSON.stringify({ urls }),
  }),
  createImportedCard: (baseUrl, deckId, payload) => requestJson(baseUrl, `/api/browser-import/decks/${deckId}/cards`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
}
