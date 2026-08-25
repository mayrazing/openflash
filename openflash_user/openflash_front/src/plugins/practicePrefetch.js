/**
 * 通用 practice.prefetch 插槽调度器。
 *
 * 插件约定：在 slots['practice.prefetch'] 处导出一个函数 `(deckId, context) => Promise<void>`，
 * 用于在进入练习前并行预热插件自身的缓存（如 eligibility、设置等），避免首屏闪烁。
 * context 透传 `{ installedIds }`，省去插件再调一次 loadDeckInstalledPlugins。
 * 注意：本插槽**固定为函数形态**，不接受 `{ component }` 风格的对象条目，
 * 与 PluginSlot 的 normalizeSlotEntry 不共用逻辑。
 *
 * 调度策略：
 * - 仅预热当前卡包已安装的插件，单个失败/超时被吞掉，不阻断练习启动；
 * - 默认每个插件超时 1000ms，独立超时；
 * - installedIds 自身加载也走 timeoutMs 上限，后端 /api/plugins/installed 卡死时
 *   按"空安装列表"降级，不让整轮 prefetch 卡死练习启动；
 * - deckId 为 null/undefined 时直接 resolve，不调任何插件 prefetch。
 *
 * 设计说明：默认插件清单走 `pluginRegistry` 中间层的 `getAllPlugins()`。registry.js
 * 含 Vite `import.meta.glob`，在 node 测试环境直接 import 会挂；中间层不含 glob，
 * 可被 node 安全静态 import。运行时 registry.js 会调 registerAllPlugins 写入清单；
 * 测试通过 options.plugins 注入 stub，此时 getAllPlugins 不会被读取。
 *
 * @param {string|number|null|undefined} deckId 当前卡包 id；空值时跳过。
 * @param {object} [options]
 * @param {number} [options.timeoutMs=1000] 单个插件 prefetch 与 installedIds 加载共用超时阈值。
 * @param {Array}  [options.plugins] 插件列表，默认走 getAllPlugins()；测试可注入。
 * @param {Array}  [options.installedIds] 预先加载好的安装 id 列表；省略则内部加载。
 * @returns {Promise<void>} 始终 resolve，永不 reject。
 */
import { getAllPlugins } from './pluginRegistry.js'
import { loadDeckInstalledPlugins } from './deckInstalledPluginLoader.js'

const inflight = new Map()

export async function runPracticePrefetch(deckId, { timeoutMs = 1000, plugins, installedIds } = {}) {
  if (deckId === null || deckId === undefined) return

  const key = String(deckId)
  if (inflight.has(key)) return inflight.get(key)

  const task = runPracticePrefetchOnce(deckId, { timeoutMs, plugins, installedIds })
    .finally(() => {
      inflight.delete(key)
    })
  inflight.set(key, task)
  return task
}

async function runPracticePrefetchOnce(deckId, { timeoutMs, plugins, installedIds }) {
  const effectivePlugins = plugins ?? getAllPlugins()
  let effectiveInstalledIds = installedIds
  if (!Array.isArray(effectiveInstalledIds)) {
    effectiveInstalledIds = await loadInstalledIdsWithTimeout(deckId, timeoutMs)
  }

  const tasks = []
  for (const plugin of effectivePlugins) {
    if (!effectiveInstalledIds.includes(plugin?.id)) continue
    const fn = plugin?.slots?.['practice.prefetch']
    if (typeof fn !== 'function') continue
    tasks.push(runOneWithTimeout(fn, deckId, timeoutMs, { installedIds: effectiveInstalledIds }))
  }
  await Promise.allSettled(tasks)
}

/**
 * 加载 installedIds 并加上整体 deadline；任何失败/超时按空数组降级，
 * 调用方据此跳过所有插件 prefetch，但不阻断练习启动。
 */
function loadInstalledIdsWithTimeout(deckId, timeoutMs) {
  return new Promise((resolve) => {
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(Array.isArray(value) ? value : [])
    }
    const timer = setTimeout(() => finish([]), timeoutMs)
    try {
      Promise.resolve(loadDeckInstalledPlugins(deckId)).then(finish, () => finish([]))
    } catch {
      finish([])
    }
  })
}

/** 把单个 prefetch 包成「超时即 resolve、错误即 resolve」的安全 promise。 */
function runOneWithTimeout(fn, deckId, timeoutMs, context) {
  return new Promise((resolve) => {
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      resolve()
    }, timeoutMs)

    const finish = () => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve()
    }

    try {
      Promise.resolve(fn(deckId, context)).then(finish, finish)
    } catch {
      finish()
    }
  })
}
