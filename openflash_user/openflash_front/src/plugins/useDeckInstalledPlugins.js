import { useEffect, useState } from 'react'
import {
  getCachedDeckInstalledPlugins,
} from './deckInstalledPluginCache'
import { loadDeckInstalledPlugins } from './deckInstalledPluginLoader'

/** 返回指定卡包已安装且全局启用的插件 id 列表，以及加载完成标记。 */
export default function useDeckInstalledPlugins(deckId) {
  const [installedIds, setInstalledIds] = useState(() => getCachedDeckInstalledPlugins(deckId) ?? [])
  const [loaded, setLoaded] = useState(() => !deckId || getCachedDeckInstalledPlugins(deckId) !== undefined)

  useEffect(() => {
    if (!deckId) {
      setInstalledIds([])
      setLoaded(true)
      return
    }
    let cancelled = false
    const cached = getCachedDeckInstalledPlugins(deckId)
    if (cached !== undefined) {
      setInstalledIds(cached)
      setLoaded(true)
      return
    }
    setLoaded(false)

    loadDeckInstalledPlugins(deckId)
      .then(ids => { if (!cancelled) setInstalledIds(ids) })
      .catch(() => { if (!cancelled) setInstalledIds([]) })
      .finally(() => { if (!cancelled) setLoaded(true) })

    return () => { cancelled = true }
  }, [deckId])

  return { installedIds, loaded }
}
