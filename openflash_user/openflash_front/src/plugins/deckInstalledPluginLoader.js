import { getInstalledPlugins } from '../db/database.js'
import {
  getCachedDeckInstalledPlugins,
  setCachedDeckInstalledPlugins,
  getDeckInstalledPluginsGeneration,
} from './deckInstalledPluginCache.js'

const inflight = new Map()

function cacheKey(deckId) {
  return String(deckId)
}

/**
 * 读取某卡包已安装插件，统一共享 cache 与同 deckId 飞行中请求。
 *
 * 缓存代次（generation）：发起请求时记录当时的 gen，结束时若 gen 已被 invalidate
 * 自增（安装/卸载触发），说明本次结果已过期，不再写回 cache——避免旧 installedIds
 * 覆盖新的安装状态。仍返回获得的 ids 给本次 await 调用方，由其自决（多数为读后即用）。
 */
export function loadDeckInstalledPlugins(deckId) {
  if (deckId == null) return Promise.resolve([])
  const cached = getCachedDeckInstalledPlugins(deckId)
  if (cached !== undefined) return Promise.resolve(cached)

  const key = cacheKey(deckId)
  if (!inflight.has(key)) {
    const startGeneration = getDeckInstalledPluginsGeneration(deckId)
    inflight.set(key, getInstalledPlugins(deckId)
      .then(ids => {
        if (getDeckInstalledPluginsGeneration(deckId) === startGeneration) {
          setCachedDeckInstalledPlugins(deckId, ids)
        }
        return ids
      })
      .finally(() => {
        inflight.delete(key)
      }))
  }
  return inflight.get(key)
}
