/** deck 已安装插件列表缓存，供进入练习前的 prefetch 与 PluginSlot 首帧共享。 */
const cache = new Map()

/** 每个 deck 的缓存代次，invalidate 时自增；飞行中的旧请求据此识别自己已过期。 */
const generations = new Map()

/** 统一 deckId key，避免 number/string 混用导致缓存不命中。 */
function cacheKey(deckId) {
  return String(deckId)
}

/** 读取某卡包已安装插件列表缓存；未命中返回 undefined。 */
export function getCachedDeckInstalledPlugins(deckId) {
  if (deckId == null) return undefined
  return cache.get(cacheKey(deckId))
}

/** 写入某卡包已安装插件列表缓存；非数组按空列表兜底。 */
export function setCachedDeckInstalledPlugins(deckId, installedIds) {
  if (deckId == null) return
  cache.set(cacheKey(deckId), Array.isArray(installedIds) ? installedIds : [])
}

/** 插件安装关系变化后失效指定卡包缓存，并使飞行中的旧请求作废。 */
export function invalidateDeckInstalledPlugins(deckId) {
  if (deckId == null) return
  const key = cacheKey(deckId)
  cache.delete(key)
  generations.set(key, (generations.get(key) ?? 0) + 1)
}

/** 读取某卡包当前缓存代次，用于飞行中请求结束时识别自己是否过期。 */
export function getDeckInstalledPluginsGeneration(deckId) {
  if (deckId == null) return 0
  return generations.get(cacheKey(deckId)) ?? 0
}
