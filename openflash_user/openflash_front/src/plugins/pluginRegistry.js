/**
 * 插件注册表中间层。
 *
 * 这个模块本身**不含** Vite 的 `import.meta.glob`，因此可以被 node:test
 * 测试代码安全 import；具体的插件清单由 `registry.js`（含 glob 的真正注册入口）
 * 通过 `registerAllPlugins` 在模块加载时写入。
 *
 * 引入这一层是为了避免「同一个 registry.js 既被静态 import（pluginSlot 等）
 * 又被动态 import（practicePrefetch）」导致 Vite 失去 chunk 优化机会；
 * 现在所有运行时消费者都走静态 import，dynamic+static 混用消除。
 */

let registered = []

/** 由 registry.js 在模块加载时调用，注册全部插件清单。可重复调用，后写覆盖前写。 */
export function registerAllPlugins(plugins) {
  registered = Array.isArray(plugins) ? plugins : []
}

/** 取已注册的插件清单。registry.js 未加载（如 node 测试场景）时返回空数组。 */
export function getAllPlugins() {
  return registered
}
