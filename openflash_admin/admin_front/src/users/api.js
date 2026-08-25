import { request } from '../api/request.js'

const USERS_PATH = '/api/admin/users'

export function listUsers(query) {
  return request(`${USERS_PATH}?query=${encodeURIComponent(query)}`)
}

export function updateUserRole(userId, role) {
  return request(`${USERS_PATH}/${encodeURIComponent(userId)}/role`, {
    method: 'PUT',
    body: JSON.stringify({ role }),
  })
}

export function updateUserCliAccess(userId, cliKey, enabled) {
  return request(`${USERS_PATH}/${encodeURIComponent(userId)}/cli-access/${encodeURIComponent(cliKey)}`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

export function updateUserOfferingAccess(userId, offeringKey, enabled) {
  return request(`/api/admin/platform-ai/offerings/${encodeURIComponent(offeringKey)}/access/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

export function updateUserBanned(userId, banned) {
  return request(`${USERS_PATH}/${encodeURIComponent(userId)}/banned`, {
    method: 'PUT',
    body: JSON.stringify({ banned }),
  })
}

export function deleteUser(userId) {
  return request(`${USERS_PATH}/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  })
}
