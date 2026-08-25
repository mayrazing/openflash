import { createContext, useContext } from 'react'

export const PluginContext = createContext({ activeIds: [], loaded: false })

/** 返回插件系统加载状态和当前激活插件 id 列表。 */
export function usePluginState() {
  return useContext(PluginContext)
}

/** 返回当前激活的插件 id 列表。 */
export function useActivePlugins() {
  return usePluginState().activeIds
}

/** 返回激活插件列表是否已经从后端完成加载。 */
export function useActivePluginsLoaded() {
  return usePluginState().loaded
}
