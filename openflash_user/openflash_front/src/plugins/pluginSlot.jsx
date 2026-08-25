import { useActivePlugins } from './pluginState'
import { ALL_PLUGINS } from './registry'
import useDeckInstalledPlugins from './useDeckInstalledPlugins'
import PluginSlotErrorBoundary from './PluginSlotErrorBoundary'

/** 把旧函数插槽和新对象插槽统一成可排序的渲染条目。 */
function normalizeSlotEntry(plugin, entry) {
  if (!entry) return null
  if (typeof entry === 'function') {
    return { pluginId: plugin.id, component: entry, order: 100 }
  }
  if (entry.component) {
    return {
      pluginId: plugin.id,
      component: entry.component,
      order: Number.isFinite(entry.order) ? entry.order : 100,
    }
  }
  return null
}

/**
 * 渲染指定插槽的组件。deckId 为 null 时按全局激活插件过滤；传入 deckId 时按卡包已安装插件过滤。
 *
 * 需要禁用外层 pointer 的覆盖层插槽，应在调用处显式传入对应 className；
 * 设置页、按钮类插槽默认必须保持可点击。
 */
export default function PluginSlot({ slotName, props = {}, className = 'space-y-3', blockClassName = 'plugin-slot-block', containerProps = {}, deckId = null }) {
  const globalActiveIds = useActivePlugins()
  const { installedIds } = useDeckInstalledPlugins(deckId)

  const allowedIds = deckId ? installedIds : globalActiveIds

  const entries = ALL_PLUGINS
    .filter(plugin => allowedIds.includes(plugin.id))
    .map(plugin => normalizeSlotEntry(plugin, plugin.slots?.[slotName]))
    .filter(Boolean)
    .sort((left, right) => left.order - right.order)

  if (entries.length === 0) return null

  return (
    <div {...containerProps} className={className}>
      {entries.map(entry => {
        const EntryComponent = entry.component
        return (
          <div key={`${slotName}:${entry.pluginId}`} className={blockClassName} data-plugin-id={entry.pluginId}>
            <PluginSlotErrorBoundary pluginId={entry.pluginId}>
              <EntryComponent {...props} />
            </PluginSlotErrorBoundary>
          </div>
        )
      })}
    </div>
  )
}
