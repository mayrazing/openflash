import { request } from '../api/request.js'

const AUTH_PATH = '/api/admin/auth'

export function loginAdmin(username, password) {
  return request(`${AUTH_PATH}/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function getCurrentAdmin() {
  return request(`${AUTH_PATH}/me`)
}

export function logoutAdmin() {
  return request(`${AUTH_PATH}/logout`, { method: 'POST' })
}
