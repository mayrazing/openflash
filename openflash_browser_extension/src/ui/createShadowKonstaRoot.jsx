import { KonstaProvider } from 'konsta/react'
import { flushSync } from 'react-dom'
import { createRoot } from 'react-dom/client'
import shadowCss from './shadow.css?inline'
import { watchSystemTheme } from './systemTheme.js'

/** 创建隔离网页样式的 Konsta React 根节点。 */
export function createShadowKonstaRoot(host) {
  const shadowRoot = host.attachShadow({ mode: 'open' })
  const style = document.createElement('style')
  const mountNode = document.createElement('div')
  style.textContent = shadowCss
  mountNode.className = 'openflash-konsta-root'
  shadowRoot.appendChild(style)
  shadowRoot.appendChild(mountNode)

  const reactRoot = createRoot(mountNode)
  const stopThemeWatcher = watchSystemTheme(mountNode)
  let mounted = true

  return {
    shadowRoot,
    mountNode,
    render(node) {
      flushSync(() => {
        reactRoot.render(
          <KonstaProvider theme="ios" dark>
            {node}
          </KonstaProvider>,
        )
      })
    },
    unmount() {
      if (!mounted) return
      mounted = false
      stopThemeWatcher()
      flushSync(() => reactRoot.unmount())
    },
  }
}
