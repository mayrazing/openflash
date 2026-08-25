import { registerAllPlugins } from './pluginRegistry.js'

const pluginModules = import.meta.glob('./*/index.{js,jsx}', { eager: true })

// 所有插件由目录入口自动注册。视觉 entry 用 { component, order }，action entry 用 { action, order }。
export const ALL_PLUGINS = Object.values(pluginModules)
  .map(module => module.default)
  .filter(Boolean)

// 把清单写入中间层，让不能直接 import 本文件（含 import.meta.glob）的纯 JS 消费者
// （如 practicePrefetch 的 node 测试路径）也能在运行时通过 getAllPlugins 取到。
registerAllPlugins(ALL_PLUGINS)
