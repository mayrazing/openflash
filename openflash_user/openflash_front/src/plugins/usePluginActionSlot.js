import { useMemo } from 'react'
import { usePluginState } from './pluginState'
import { ALL_PLUGINS } from './registry'
import useDeckInstalledPlugins from './useDeckInstalledPlugins'
import { appWarn } from '../lib/appLog'

const EMPTY_PROPS = {}

/** 把插件 action 插槽统一成可排序的非视觉动作条目。 */
function normalizeActionEntry(plugin, entry) {
  if (!entry?.action) return null
  return {
    pluginId: plugin.id,
    action: entry.action,
    order: Number.isFinite(entry.order) ? entry.order : 100,
  }
}

/** 执行插件 action 工厂；单个插件失败时丢弃该 action，避免拖垮核心页面。 */
function createAction(entry, props) {
  try {
    return {
      pluginId: entry.pluginId,
      ...entry.action(props),
    }
  } catch (error) {
    appWarn(60003, 'Plugin action slot failed', entry.pluginId, error)
    return null
  }
}

/** 返回 action slot 的加载态和动作列表。deckId 为 null 时按全局激活插件过滤；传入 deckId 时按卡包已安装插件过滤。 */
export function usePluginActionSlotState(slotName, props = EMPTY_PROPS, deckId = null) {
  const { activeIds: globalActiveIds, loaded: globalLoaded } = usePluginState()
  const { installedIds, loaded: deckLoaded } = useDeckInstalledPlugins(deckId)

  const allowedIds = deckId ? installedIds : globalActiveIds
  const effectiveLoaded = deckId ? deckLoaded : globalLoaded

  const actions = useMemo(() => ALL_PLUGINS
    .filter(plugin => allowedIds.includes(plugin.id))
    .map(plugin => normalizeActionEntry(plugin, plugin.slots?.[slotName]))
    .filter(Boolean)
    .sort((left, right) => left.order - right.order)
    .map(entry => createAction(entry, props))
    .filter(Boolean), [allowedIds, slotName, props])

  return useMemo(() => ({ loaded: effectiveLoaded, actions }), [effectiveLoaded, actions])
}

/** 返回已启用插件注册的非视觉 action slot。deckId 传入时按卡包已安装插件过滤；不传时按全局激活插件过滤。 */
export default function usePluginActionSlot(slotName, props = EMPTY_PROPS, deckId = null) {
  return usePluginActionSlotState(slotName, props, deckId).actions
}
