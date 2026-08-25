import { DEFAULT_SERVICE_URL } from './config.js'

const keys = {
  serviceUrl: 'serviceUrl',
  selectedDeckId: 'selectedDeckId',
  defaultDeckId: 'defaultDeckId',
  lastImportStatus: 'lastImportStatus',
}

export async function getServiceUrl() {
  const result = await chrome.storage.local.get(keys.serviceUrl)
  return normalizeServiceUrl(result[keys.serviceUrl] || DEFAULT_SERVICE_URL)
}

export async function setServiceUrl(value) {
  await chrome.storage.local.set({ [keys.serviceUrl]: normalizeServiceUrl(value) })
}

export async function getSelectedDeckId() {
  const result = await chrome.storage.local.get(keys.selectedDeckId)
  return result[keys.selectedDeckId] || null
}

export async function setSelectedDeckId(deckId) {
  await chrome.storage.local.set({ [keys.selectedDeckId]: deckId == null ? null : String(deckId) })
}

export async function getDefaultDeckId() {
  const result = await chrome.storage.local.get(keys.defaultDeckId)
  return result[keys.defaultDeckId] || null
}

export async function setDefaultDeckId(deckId) {
  await chrome.storage.local.set({ [keys.defaultDeckId]: deckId == null ? null : String(deckId) })
}

export async function getLastImportStatus() {
  const result = await chrome.storage.local.get(keys.lastImportStatus)
  return result[keys.lastImportStatus] || null
}

export async function setLastImportStatus(status) {
  await chrome.storage.local.set({ [keys.lastImportStatus]: status || null })
}

export function normalizeServiceUrl(value) {
  return String(value || DEFAULT_SERVICE_URL).trim().replace(/\/+$/, '')
}
