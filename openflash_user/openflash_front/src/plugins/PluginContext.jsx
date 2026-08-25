import { useEffect, useState } from 'react'
import { buildApiUrl } from '../db/database'
import { PluginContext } from './pluginState'

/** 从后端拉取激活插件列表，注入到子树。 */
export function PluginProvider({ children }) {
  const [activeIds, setActiveIds] = useState([])
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    fetch(buildApiUrl('/api/plugins/active'), { credentials: 'include' })
      .then(r => r.json())
      .then(json => { if (Array.isArray(json.data)) setActiveIds(json.data) })
      .catch(() => {})
      .finally(() => setLoaded(true))
  }, [])

  return (
    <PluginContext.Provider value={{ activeIds, loaded }}>
      {children}
    </PluginContext.Provider>
  )
}
