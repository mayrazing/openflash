import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test, { after } from 'node:test'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { KonstaProvider } from 'konsta/react'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

const extensionRoot = fileURLToPath(new URL('..', import.meta.url))
let viteServer

after(async () => {
  await viteServer?.close()
})

async function loadModules() {
  viteServer ||= await createServer({
    appType: 'custom',
    configFile: false,
    optimizeDeps: { noDiscovery: true },
    plugins: [react(), tailwindcss()],
    root: extensionRoot,
    server: { middlewareMode: true, hmr: false, ws: false },
  })
  const [controllerModule, toastModule] = await Promise.all([
    viteServer.ssrLoadModule('/src/content/createPageNotification.jsx'),
    viteServer.ssrLoadModule('/src/content/PageNotificationToast.jsx'),
  ])
  return {
    createPageNotification: controllerModule.createPageNotification,
    PageNotificationToast: toastModule.default,
  }
}

function createElementNode(tagName) {
  return {
    tagName: tagName.toUpperCase(),
    children: [],
    style: {},
    appendChild(child) {
      child.remove?.()
      this.children.push(child)
      child.parentNode = this
      return child
    },
    remove() {
      if (!this.parentNode) return
      this.parentNode.children = this.parentNode.children.filter((child) => child !== this)
      this.parentNode = null
    },
  }
}

function importantUtility(...parts) {
  return `!${parts.join('-')}`
}

function renderToast(PageNotificationToast, notification) {
  return renderToStaticMarkup(
    createElement(
      KonstaProvider,
      { theme: 'ios' },
      createElement(PageNotificationToast, { notification, opened: true }),
    ),
  )
}

function findGlassTag(html) {
  return html.match(/<div[^>]*class="[^"]*\sk-glass(?:\s|")[^"]*"[^>]*>/)?.[0] || ''
}

/** 创建可手动触发的测试定时器。 */
function createTestTimers() {
  const scheduled = []
  const cleared = []
  return {
    scheduled,
    cleared,
    setTimeout(callback, delay) {
      const id = scheduled.length + 1
      scheduled.push({ id, callback, delay })
      return id
    },
    clearTimeout(id) {
      cleared.push(id)
    },
  }
}

async function createHarness() {
  const { createPageNotification, PageNotificationToast } = await loadModules()
  const body = createElementNode('body')
  const roots = []
  const frames = []
  const timers = createTestTimers()
  const controller = createPageNotification({
    document: { body, createElement: createElementNode },
    requestAnimationFrame(callback) {
      frames.push(callback)
      callback()
      return frames.length
    },
    setTimeout: timers.setTimeout,
    clearTimeout: timers.clearTimeout,
    createShadowRoot(host) {
      const root = {
        host,
        renders: [],
        render(node) {
          this.current = node
          this.renders.push(node)
        },
        unmount() {
          this.unmounted = true
        },
      }
      roots.push(root)
      return root
    },
  })
  return { body, controller, frames, PageNotificationToast, roots, timers }
}

test('page notification exposes controller and Konsta Toast component', async () => {
  const { createPageNotification, PageNotificationToast } = await loadModules()
  const source = await readFile(new URL('../src/content/PageNotificationToast.jsx', import.meta.url), 'utf8')

  assert.equal(typeof createPageNotification, 'function')
  assert.equal(typeof PageNotificationToast, 'function')
  assert.match(source, /import\s*\{\s*Toast\s*\}\s*from\s*['"]konsta\/react['"]/)
})

test('show lazily creates and reuses one Shadow host while updating message and level', async () => {
  const harness = await createHarness()

  assert.equal(harness.body.children.length, 0)
  harness.controller.show({ message: '已保存', level: 'success' })
  harness.controller.show({ message: '请先登录', level: 'error' })

  assert.equal(harness.body.children.length, 1)
  assert.equal(harness.roots.length, 1)
  assert.equal(harness.roots[0].current.type, harness.PageNotificationToast)
  assert.equal(harness.roots[0].current.props.notification.id, 2)
  assert.equal(harness.roots[0].current.props.notification.message, '请先登录')
  assert.equal(harness.roots[0].current.props.notification.level, 'error')
  assert.equal(harness.roots[0].current.props.opened, true)
})

test('show resets the 2300ms timer and only the current timer closes Toast', async () => {
  const harness = await createHarness()

  harness.controller.show({ message: '第一次', level: 'success' })
  harness.controller.show({ message: '第二次', level: 'warning' })

  assert.deepEqual(harness.timers.cleared, [1])
  assert.deepEqual(harness.timers.scheduled.map(({ delay }) => delay), [2300, 2300])
  harness.timers.scheduled[0].callback()
  assert.equal(harness.roots[0].current.props.opened, true)
  harness.timers.scheduled[1].callback()
  assert.equal(harness.roots[0].current.props.opened, false)
})

test('destroy clears timer, unmounts React and removes host', async () => {
  const harness = await createHarness()
  harness.controller.show({ message: '已保存', level: 'success' })

  harness.controller.destroy()

  assert.deepEqual(harness.timers.cleared, [1])
  assert.equal(harness.roots[0].unmounted, true)
  assert.equal(harness.body.children.length, 0)
})

test('Toast maps every level to important Apple semantic colors', async () => {
  const { PageNotificationToast } = await loadModules()
  const expected = {
    success: [importantUtility('bg', 'app', 'success', 'fill'), importantUtility('text', 'app', 'on', 'success')],
    warning: [importantUtility('bg', 'app', 'warning', 'fill'), importantUtility('text', 'app', 'on', 'warning')],
    error: [importantUtility('bg', 'app', 'danger', 'fill'), importantUtility('text', 'app', 'on', 'danger')],
  }

  for (const [level, classes] of Object.entries(expected)) {
    const html = renderToast(PageNotificationToast, { id: 1, message: level, level })
    const glass = findGlassTag(html)
    for (const className of classes) assert.ok(glass.includes(className), `${level} 缺少 ${className}`)
  }
})

test('unknown level falls back to important Apple danger colors', async () => {
  const { PageNotificationToast } = await loadModules()
  const html = renderToast(PageNotificationToast, { id: 1, message: 'unknown', level: 'unknown' })
  const glass = findGlassTag(html)

  assert.ok(glass.includes(importantUtility('bg', 'app', 'danger', 'fill')))
  assert.ok(glass.includes(importantUtility('text', 'app', 'on', 'danger')))
})

test('Toast keeps status semantics and large HUD dimensions', async () => {
  const { PageNotificationToast } = await loadModules()
  const html = renderToast(PageNotificationToast, { id: 1, message: '已保存', level: 'success' })
  const toast = html.match(/<div[^>]*class="[^"]*k-toast[^"]*"[^>]*>/)?.[0] || ''
  const glass = findGlassTag(html)
  const content = html.match(/\sk-glass(?:\s|")[^"]*"[^>]*><div[^>]*class="([^"]*)"/)?.[1] || ''

  assert.match(toast, /\[&amp;_\.k-glass\]:!max-w-\[min\(720px,calc\(100vw-48px\)\)\]/)
  assert.match(toast, /\[&amp;_\.k-glass&gt;div\]:!p-0/)
  assert.match(toast, /role="status"[^>]*aria-live="polite"/)
  assert.match(glass, /max-w-lg/)
  assert.match(content, /pl-4[^>]*pr-4[^>]*pt-3[^>]*pb-3/)
  assert.match(html, /<span[^>]*class="[^"]*px-9[^"]*py-6[^"]*text-\[28px\][^"]*leading-\[1\.45\][^"]*font-medium[^"]*"[^>]*>已保存<\/span>/)
})

test('important semantic utilities override Konsta glass colors in compiled Shadow CSS', async () => {
  await loadModules()
  const { default: css } = await viteServer.ssrLoadModule('/src/ui/shadow.css?inline')
  const expected = {
    success: ['background-color: var(--app-success-fill) !important', 'color: var(--app-on-success) !important'],
    warning: ['background-color: var(--app-warning-fill) !important', 'color: var(--app-on-warning) !important'],
    danger: ['background-color: var(--app-danger-fill) !important', 'color: var(--app-on-danger) !important'],
  }

  for (const [level, declarations] of Object.entries(expected)) {
    const backgroundClass = importantUtility('bg', 'app', level, 'fill')
    const textClass = importantUtility('text', 'app', 'on', level)
    const backgroundRule = css.match(new RegExp(`\\\\${backgroundClass}\\s*\\{([^}]*)\\}`))?.[1] || ''
    const textRule = css.match(new RegExp(`\\\\${textClass}\\s*\\{([^}]*)\\}`))?.[1] || ''
    assert.ok(backgroundRule.includes(declarations[0]), `${backgroundClass} 未生成 !important 背景`)
    assert.ok(textRule.includes(declarations[1]), `${textClass} 未生成 !important 文字色`)
  }
})

test('compiled Shadow CSS overrides Konsta Toast width and content padding', async () => {
  await loadModules()
  const { default: css } = await viteServer.ssrLoadModule('/src/ui/shadow.css?inline')
  const maxWidthRule = css.match(/[^{}]*k-glass[^{}]*\{[^{}]*max-width:\s*min\(720px,\s*calc\(100vw\s*-\s*48px\)\)\s*!important;?[^{}]*\}/)?.[0] || ''
  const paddingRule = css.match(/[^{}]*k-glass[^{}]*>\s*div[^{}]*\{[^{}]*padding:\s*0(?:px)?\s*!important;?[^{}]*\}/)?.[0] || ''

  assert.ok(maxWidthRule, '缺少覆盖 .k-glass max-w-lg 的 720px !important 规则')
  assert.ok(paddingRule, '缺少清除 Konsta content padding 的 !important 规则')
})
