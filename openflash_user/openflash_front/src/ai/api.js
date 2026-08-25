import { request } from '../db/database.js'

/** 读取当前用户 AI 供应商列表. */
export async function getAiProviders() {
  return request('/api/settings/ai-config/providers')
}

/** 读取平台 CLI offering 的 runtime 状态与模型目录. */
export async function getPlatformModels(offeringKey) {
  return request(`/api/settings/ai-config/platform-offerings/${encodeURIComponent(offeringKey)}/models`)
}

/** 保存当前用户的平台 CLI 偏好; 后端负责校验且不会自动启用. */
export async function savePlatformPreference(offeringKey, { model, reasoningEffort }) {
  return request(`/api/settings/ai-config/platform-offerings/${encodeURIComponent(offeringKey)}/preference`, {
    method: 'PUT',
    body: JSON.stringify({ model, reasoningEffort }),
  })
}

/** 保存当前用户某个 AI 供应商. */
export async function saveAiProvider(providerKey, payload) {
  return request(`/api/settings/ai-config/providers/${encodeURIComponent(providerKey)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/** 新建当前用户 AI 供应商; 内部 key 由后端生成. */
export async function createAiProvider(payload) {
  return request('/api/settings/ai-config/providers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** 设置当前启用 AI 供应商. */
export async function activateAiProvider(providerKey) {
  return request(`/api/settings/ai-config/providers/${encodeURIComponent(providerKey)}/activate?source=USER`, {
    method: 'POST',
  })
}

/** 启用一个平台 offering, 由后端统一替换当前唯一来源. */
export async function activatePlatformOffering(offeringKey) {
  return request(`/api/settings/ai-config/platform-offerings/${encodeURIComponent(offeringKey)}/activate`, {
    method: 'POST',
  })
}

/** 删除未启用 AI 供应商. */
export async function deleteAiProvider(providerKey) {
  return request(`/api/settings/ai-config/providers/${encodeURIComponent(providerKey)}`, {
    method: 'DELETE',
  })
}

/** 使用临时连接配置读取上游可用模型, 不保存配置. */
export async function discoverAiModels(providerKey, baseUrl, apiKey) {
  return request('/api/settings/ai-config/models/discover', {
    method: 'POST',
    body: JSON.stringify({ providerKey: providerKey || null, baseUrl, apiKey: apiKey || null }),
  })
}
