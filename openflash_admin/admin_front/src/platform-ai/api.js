import { request } from '../api/request.js'

const PLATFORM_AI_PATH = '/api/admin/platform-ai'
const connectionPath = key => `${PLATFORM_AI_PATH}/connections/${encodeURIComponent(key)}`
const offeringPath = key => `${PLATFORM_AI_PATH}/offerings/${encodeURIComponent(key)}`

export function getPlatformAiPage() {
  return request(PLATFORM_AI_PATH)
}

export function createPlatformConnection(payload) {
  return request(`${PLATFORM_AI_PATH}/connections`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function updatePlatformConnection(connectionKey, payload) {
  return request(connectionPath(connectionKey), {
    method: 'PUT', body: JSON.stringify(payload),
  })
}

export function replacePlatformCredentials(connectionKey, apiKey) {
  return request(`${connectionPath(connectionKey)}/credentials`, {
    method: 'PUT', body: JSON.stringify({ apiKey }),
  })
}

export function deletePlatformConnection(connectionKey) {
  return request(connectionPath(connectionKey), { method: 'DELETE' })
}

export function discoverPlatformModels(connectionKey) {
  return request(`${connectionPath(connectionKey)}/models/discover`, { method: 'POST' })
}

export function discoverPlatformModelsForConfiguration(baseUrl, apiKey) {
  return request(`${PLATFORM_AI_PATH}/models/discover`, {
    method: 'POST', body: JSON.stringify({ baseUrl, apiKey }),
  })
}

export function createPlatformOffering(connectionKey, modelKey, sortOrder) {
  return request(`${connectionPath(connectionKey)}/offerings`, {
    method: 'POST', body: JSON.stringify({ modelKey, sortOrder }),
  })
}

export function updatePlatformOffering(offeringKey, payload) {
  return request(offeringPath(offeringKey), {
    method: 'PUT', body: JSON.stringify(payload),
  })
}

export function deletePlatformOffering(offeringKey) {
  return request(offeringPath(offeringKey), { method: 'DELETE' })
}

export function setPlatformOfferingDefaultAccess(offeringKey, enabled) {
  return request(`${offeringPath(offeringKey)}/access/default`, {
    method: 'PUT', body: JSON.stringify({ enabled }),
  })
}

export function setPlatformOfferingUserAccess(offeringKey, userId, enabled) {
  return request(`${offeringPath(offeringKey)}/access/users/${encodeURIComponent(userId)}`, {
    method: 'PUT', body: JSON.stringify({ enabled }),
  })
}

export function deletePlatformOfferingUserAccess(offeringKey, userId) {
  return request(`${offeringPath(offeringKey)}/access/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  })
}
