import { normalizeServiceUrl } from './storage.js'

/**
 * 归一化并保存服务地址输入框当前值，返回后续操作应使用的地址。
 * @param {string} value
 * @param {(value: string) => Promise<void>} save
 * @returns {Promise<string>}
 */
export async function persistServiceUrlValue(value, save) {
  const serviceUrl = normalizeServiceUrl(value)
  await save(serviceUrl)
  return serviceUrl
}

/**
 * 根据当前服务地址生成登录页地址。
 * @param {string} serviceUrl
 * @returns {string}
 */
export function buildAuthUrl(serviceUrl) {
  return `${normalizeServiceUrl(serviceUrl)}/auth`
}
