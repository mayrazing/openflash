import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test, { after } from 'node:test'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
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
    server: { middlewareMode: true },
  })
  const [dialogModule, windowModule] = await Promise.all([
    viteServer.ssrLoadModule('/src/content/createManualCardDialog.jsx'),
    viteServer.ssrLoadModule('/src/content/ManualCardWindow.jsx'),
  ])
  return {
    createManualCardDialog: dialogModule.createManualCardDialog,
    ManualCardWindow: windowModule.default,
  }
}

function element(tagName) {
  const queryMap = new Map()
  const node = {
    tagName: tagName.toUpperCase(),
    className: '',
    style: {},
    dataset: {},
    children: [],
    childNodes: [],
    listeners: {},
    _innerHTML: '',
    textContent: '',
    appendChild(child) {
      if (child.tagName === '#DOCUMENT-FRAGMENT') {
        for (const entry of [...child.childNodes]) this.appendChild(entry)
        return child
      }
      child.remove?.()
      this.children.push(child)
      this.childNodes.push(child)
      child.parentNode = this
      return child
    },
    attachShadow(options) {
      this.attachShadowOptions = options
      this.shadowRoot = element('#shadow-root')
      this.shadowRoot.host = this
      this.shadowRoot.parentNode = this
      return this.shadowRoot
    },
    addEventListener(type, listener, options = {}) {
      if (!this.listeners[type]) this.listeners[type] = []
      this.listeners[type].push({ listener, capture: Boolean(options?.capture) })
    },
    dispatch(type, event = {}) { return dispatchEvent(this, type, event) },
    focus() { this.focused = true },
    remove() {
      this.removed = true
      if (this.parentNode) {
        this.parentNode.children = this.parentNode.children.filter((child) => child !== this)
        this.parentNode.childNodes = this.parentNode.childNodes.filter((child) => child !== this)
        this.parentNode = null
      }
    },
    contains(child) {
      if (child === this) return true
      return this.children.some((entry) => entry.contains?.(child))
    },
    closest(selector) {
      if (selector === '[data-role="drag"]' && this.dataset.role === 'drag') return this
      if (selector === '[data-remove-image]' && this.dataset.removeImage) return this
      if (selector === '[data-side]' && this.dataset.side) return this
      if (selector === '[data-openflash-image-id]' && this.dataset.openflashImageId) return this
      return this.parentNode?.closest?.(selector) || null
    },
    querySelector(selector) {
      if (selector === '.caret-anchor' && this.caretAnchor) return this.caretAnchor
      if (!queryMap.has(selector)) {
        const child = element(selector)
        child.parentNode = this
        const side = selector.match(/^\[data-side="([ab])"\]$/)?.[1]
        if (side) child.dataset.side = side
        if (selector === '[data-role="drag"]') child.dataset.role = 'drag'
        queryMap.set(selector, child)
      }
      return queryMap.get(selector)
    },
    querySelectorAll(selector) {
      if (selector !== '[data-openflash-image-id]') return []
      return this.children.flatMap((child) => [
        ...(child.dataset?.openflashImageId ? [child] : []),
        ...child.querySelectorAll(selector),
      ])
    },
    setPointerCapture(pointerId) { this.pointerCapture = pointerId },
  }
  node.classList = {
    contains(className) {
      return node.className.split(/\s+/).includes(className)
    },
  }
  Object.defineProperty(node, 'nextElementSibling', {
    get() {
      const siblings = this.parentNode?.children || []
      return siblings[siblings.indexOf(this) + 1] || null
    },
  })
  Object.defineProperty(node, 'innerHTML', {
    get() { return this._innerHTML },
    set(value) {
      this._innerHTML = String(value || '')
      const match = this._innerHTML.match(/data-openflash-image-id="([^"]+)"/)
      this.firstElementChild = match ? element('span') : null
      if (this.firstElementChild) {
        this.firstElementChild.dataset.openflashImageId = match[1]
        this.appendChild(this.firstElementChild)
      }
      this.caretAnchor = /class="[^"]*\bcaret-anchor\b/.test(this._innerHTML) ? element('span') : null
      if (this.caretAnchor) {
        this.caretAnchor.className = 'caret-anchor'
        this.caretAnchor.textContent = '\u200B'
        this.caretAnchor.firstChild = { nodeType: 3, parentNode: this.caretAnchor, textContent: '\u200B' }
        this.caretAnchor.childNodes.push(this.caretAnchor.firstChild)
        this.appendChild(this.caretAnchor)
      }
    },
  })
  return node
}

function dispatchEvent(target, type, event = {}) {
  let stopped = false
  let immediateStopped = false
  let result
  const dispatched = {
    ...event,
    target: event.target || target,
    currentTarget: target,
    defaultPrevented: false,
    stopPropagation() {
      stopped = true
      event.stopPropagation?.()
    },
    stopImmediatePropagation() {
      stopped = true
      immediateStopped = true
      event.stopImmediatePropagation?.()
    },
    preventDefault() {
      dispatched.defaultPrevented = true
      event.preventDefault?.()
    },
  }
  const path = []
  for (let node = target; node; node = node.parentNode) path.push(node)
  for (const node of path) {
    for (const entry of node.listeners?.[type] || []) {
      if (entry.capture) continue
      const value = entry.listener(dispatched)
      if (value !== undefined && result === undefined) result = value
      if (immediateStopped) break
    }
    if (stopped) break
  }
  return result
}

function defaultEditor() {
  return {
    createState: (text) => ({
      a: { text, images: [], imageOrder: [] },
      b: { text: '', images: [], imageOrder: [] },
      composing: false,
      error: '',
      saving: false,
    }),
    hasAnyContent: (state) => Boolean(state.a.text || state.a.images.length || state.b.text || state.b.images.length),
    imagesFromClipboardItems: () => [],
    readSideFromEditor: (node) => ({
      text: node.textContent || '',
      imageOrder: node.querySelectorAll('[data-openflash-image-id]').map((image) => image.dataset.openflashImageId),
    }),
  }
}

async function loadDialog(overrides = {}) {
  const { createManualCardDialog, ManualCardWindow } = await loadModules()
  const body = element('body')
  const roots = []
  let selectedRange = null
  const pageDocument = {
    body,
    createElement: element,
    createDocumentFragment: () => element('#document-fragment'),
    createRange: () => ({
      startContainer: null,
      startOffset: 0,
      get commonAncestorContainer() { return this.startContainer },
      setStart(node, offset) {
        this.startContainer = node
        this.startOffset = offset
      },
      setStartAfter(node) {
        this.startAfter = node
        this.startContainer = node.parentNode
        this.startOffset = node.parentNode.childNodes.indexOf(node) + 1
      },
      collapse(value) { this.collapsed = value },
      deleteContents() {},
      insertNode(insertedNode) {
        const nodes = insertedNode.tagName === '#DOCUMENT-FRAGMENT'
          ? [...insertedNode.childNodes]
          : [insertedNode]
        for (const child of nodes) {
          child.remove?.()
          const elementOffset = this.startContainer.childNodes
            .slice(0, this.startOffset)
            .filter((entry) => entry.tagName)
            .length
          this.startContainer.childNodes.splice(this.startOffset, 0, child)
          this.startContainer.children.splice(elementOffset, 0, child)
          child.parentNode = this.startContainer
          this.startOffset += 1
        }
      },
    }),
  }
  const selection = {
    toString: () => 'selected',
    get rangeCount() { return selectedRange ? 1 : 0 },
    getRangeAt: () => selectedRange,
    removeAllRanges() { selectedRange = null },
    addRange(range) { selectedRange = range },
    placeAtEnd(node) {
      selectedRange = pageDocument.createRange()
      selectedRange.setStart(node, node.childNodes.length)
    },
  }
  const pageWindow = {
    innerHeight: 700,
    innerWidth: 1000,
    getSelection: () => selection,
  }
  const chromeApi = {
    storage: { local: { get: async () => ({ manualCardPosition: null }), set: async () => {} } },
    runtime: { sendMessage: async () => ({ ok: true }) },
  }
  const createShadowRoot = (host) => {
    const rawRoot = host.attachShadow({ mode: 'open' })
    const root = {
      shadowRoot: rawRoot,
      mountNode: element('div'),
      renders: [],
      render(node) {
        this.renders.push(node)
        this.current = node
      },
      unmount() { this.unmounted = true },
    }
    roots.push(root)
    return root
  }
  const deps = {
    chromeApi,
    createSaver: () => ({ save: async () => ({ id: 1 }) }),
    createShadowRoot,
    document: pageDocument,
    editor: defaultEditor(),
    window: pageWindow,
    ...overrides,
  }
  const dialog = createManualCardDialog(deps)
  return { body, chromeApi, dialog, ManualCardWindow, pageDocument, pageWindow, roots, selection }
}

function currentView(root) {
  return root.current.type(root.current.props)
}

function findByRole(node, role) {
  if (!node || typeof node !== 'object') return null
  if (node.props?.['data-role'] === role) return node
  const children = Array.isArray(node.props?.children) ? node.props.children : [node.props?.children]
  for (const child of children.flat(Infinity)) {
    const found = findByRole(child, role)
    if (found) return found
  }
  return null
}

test('manual card React view uses Konsta Card and Button without fixed hex colors', async () => {
  const source = await readFile(new URL('../src/content/ManualCardWindow.jsx', import.meta.url), 'utf8')
  const controllerSource = await readFile(new URL('../src/content/createManualCardDialog.jsx', import.meta.url), 'utf8')

  assert.match(source, /import\s*\{[^}]*Button[^}]*Card[^}]*\}\s*from\s*['"]konsta\/react['"]/s)
  assert.doesNotMatch(source, /#[0-9a-f]{3,8}\b/i)
  assert.match(source, /<header[^>]*data-role="drag"/)
  assert.match(source, /contentEditable/)
  assert.match(source, /h-\[72px\]/)
  assert.match(source, /w-\[72px\]/)
  assert.equal(source.match(/onClick=\{onRemoveImage\}/g)?.length, 1)
  assert.doesNotMatch(controllerSource, /addEventListener\('click', removeImage\)/)
})

test('manual card Konsta Card emits order-independent zero margin and full width', async () => {
  const { ManualCardWindow } = await loadModules()
  const html = renderToStaticMarkup(createElement(ManualCardWindow, {
    labels: {},
    mode: 'edit',
    onCancel() {},
    onEditorInput() {},
    onEditorPaste() {},
    onRemoveImage() {},
    onSave() {},
    state: {
      a: { text: '', images: [], imageOrder: [] },
      b: { text: '', images: [], imageOrder: [] },
      saving: false,
    },
  }))

  assert.match(html, /style="[^"]*margin:0[^"]*width:100%[^"]*"/)
})

test('manual card React view places a caret anchor after every image chip', async () => {
  const { ManualCardWindow } = await loadModules()
  const sideA = {
    text: 'front',
    images: [
      { id: 'a1', previewUrl: 'blob:a1' },
      { id: 'a2', previewUrl: 'blob:a2' },
    ],
    imageOrder: ['a1', 'a2'],
  }
  const html = renderToStaticMarkup(createElement(ManualCardWindow, {
    labels: {},
    mode: 'edit',
    onCancel() {},
    onEditorInput() {},
    onEditorPaste() {},
    onRemoveImage() {},
    onSave() {},
    state: { a: sideA, b: { text: '', images: [], imageOrder: [] }, saving: false },
  }))
  const firstChip = html.indexOf('data-openflash-image-id="a1"')
  const firstAnchor = html.indexOf('caret-anchor', firstChip)
  const secondChip = html.indexOf('data-openflash-image-id="a2"')

  assert.ok(firstChip < firstAnchor && firstAnchor < secondChip)
})

test('Shadow Konsta root uses open Shadow DOM, inline CSS, theme watcher and iOS provider', async () => {
  const source = await readFile(new URL('../src/ui/createShadowKonstaRoot.jsx', import.meta.url), 'utf8')

  assert.match(source, /shadow\.css\?inline/)
  assert.match(source, /attachShadow\(\{ mode: 'open' \}\)/)
  assert.match(source, /openflash-konsta-root/)
  assert.match(source, /watchSystemTheme/)
  assert.match(source, /<KonstaProvider[^>]*theme="ios"[^>]*dark/s)
})

test('manual card dialog creates one shadow host and focuses repeated open', async () => {
  const { dialog, body } = await loadDialog()

  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })

  assert.equal(body.children.length, 1)
  assert.equal(body.children[0].focused, true)
})

test('manual card dialog prefers selected text supplied by its trusted window context', async () => {
  const initialTexts = []
  const editor = defaultEditor()
  const createState = editor.createState
  editor.createState = (text) => {
    initialTexts.push(text)
    return createState(text)
  }
  const { dialog } = await loadDialog({ editor })

  await dialog.open({
    deckId: '7',
    baseUrl: 'http://localhost:5173',
    selectedText: 'trusted selected text',
  })

  assert.deepEqual(initialTexts, ['trusted selected text'])
})

test('manual card dialog calls its owner when the dialog closes', async () => {
  let closes = 0
  const { dialog } = await loadDialog({ onClosed: () => { closes += 1 } })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })

  dialog.close()
  dialog.close()

  assert.equal(closes, 1)
})

test('manual card dialog clamps viewport position and saves drag end', async () => {
  const saved = []
  const { dialog, body } = await loadDialog({
    chromeApi: {
      storage: {
        local: {
          get: async () => ({ manualCardPosition: { left: 5000, top: 4000 } }),
          set: async (value) => saved.push(value.manualCardPosition),
        },
      },
      runtime: { sendMessage: async () => ({ ok: true }) },
    },
  })

  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  assert.equal(body.children[0].style.left, '560px')
  assert.equal(body.children[0].style.top, '320px')
  await dialog.handleDragEndForTest({ left: 30, top: 40 })
  assert.deepEqual(saved, [{ left: 30, top: 40 }])
})

test('manual card dialog Escape enters confirmation then returns to editing', async () => {
  const { dialog } = await loadDialog()
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  dialog.setStateForTest({
    a: { text: 'front', images: [], imageOrder: [] },
    b: { text: '', images: [], imageOrder: [] },
  })

  dialog.handleEscapeForTest()
  assert.equal(dialog.getModeForTest(), 'confirm')
  dialog.handleEscapeForTest()
  assert.equal(dialog.getModeForTest(), 'edit')
})

test('manual card dialog focuses side A editor after opening', async () => {
  const { dialog, body } = await loadDialog()
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })

  assert.equal(body.children[0].shadowRoot.querySelector('[data-side="a"]').focused, true)
})

test('manual card dialog isolates keyboard, input, paste and pointer events from page', async () => {
  const { dialog, body } = await loadDialog()
  const escaped = []
  for (const type of ['keydown', 'input', 'paste', 'pointerdown']) {
    body.addEventListener(type, () => escaped.push(type))
  }
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  const editor = body.children[0].shadowRoot.querySelector('[data-side="a"]')

  for (const type of ['keydown', 'input', 'paste', 'pointerdown']) editor.dispatch(type, { key: 's' })

  assert.deepEqual(escaped, [])
})

test('manual card dialog inserts 72px image chip and caret anchor inside editor', async () => {
  const outsidePageNode = element('p')
  const addedRanges = []
  const editorApi = defaultEditor()
  editorApi.imagesFromClipboardItems = () => [{ id: 'a1', file: { name: 'a.png' }, previewUrl: 'blob:a1' }]
  const { dialog, body, roots } = await loadDialog({
    editor: editorApi,
    window: {
      innerHeight: 700,
      innerWidth: 1000,
      getSelection: () => ({
        toString: () => '',
        rangeCount: 1,
        getRangeAt: () => ({
          commonAncestorContainer: outsidePageNode,
          deleteContents() {},
          insertNode() { throw new Error('must not insert into page selection') },
        }),
        removeAllRanges() {},
        addRange(range) { addedRanges.push(range) },
      }),
    },
  })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  const editor = body.children[0].shadowRoot.querySelector('[data-side="a"]')
  let prevented = false

  roots[0].current.props.onEditorPaste('a', {
    clipboardData: { items: [{ type: 'image/png' }] },
    currentTarget: editor,
    preventDefault() { prevented = true },
  })

  assert.equal(prevented, true)
  assert.deepEqual(editor.querySelectorAll('[data-openflash-image-id]').map((image) => image.dataset.openflashImageId), ['a1'])
  assert.equal(editor.children.some((child) => child.className === 'caret-anchor'), true)
  assert.equal(addedRanges[0].startAfter.textContent, '\u200B')
  assert.equal(addedRanges[0].collapsed, true)
  assert.equal(editor.focused, true)
})

test('manual card dialog keeps consecutive pasted images as siblings and deleting first preserves second', async () => {
  const editorApi = defaultEditor()
  editorApi.imagesFromClipboardItems = () => [
    { id: 'a1', file: { name: 'a.png' }, previewUrl: 'blob:a1' },
    { id: 'a2', file: { name: 'b.png' }, previewUrl: 'blob:a2' },
  ]
  const { dialog, body, roots, selection } = await loadDialog({ editor: editorApi })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  const editor = body.children[0].shadowRoot.querySelector('[data-side="a"]')
  selection.placeAtEnd(editor)

  roots[0].current.props.onEditorPaste('a', {
    clipboardData: { items: [{ type: 'image/png' }, { type: 'image/png' }] },
    currentTarget: editor,
    preventDefault() {},
  })
  const chips = editor.querySelectorAll('[data-openflash-image-id]')
  const firstChip = chips.find((chip) => chip.dataset.openflashImageId === 'a1')
  const secondChip = chips.find((chip) => chip.dataset.openflashImageId === 'a2')
  const siblingChips = firstChip.parentNode === editor && secondChip.parentNode === editor
  roots[0].current.props.onRemoveImage({
    target: {
      closest(selector) {
        if (selector === '[data-remove-image]') return this
        if (selector === '[data-openflash-image-id]') return firstChip
        return null
      },
      dataset: { removeImage: 'a1', sideKey: 'a' },
    },
  })

  assert.deepEqual({
    siblingChips,
    remainingImageIds: editor.querySelectorAll('[data-openflash-image-id]').map((chip) => chip.dataset.openflashImageId),
  }, {
    siblingChips: true,
    remainingImageIds: ['a2'],
  })
})

test('manual card dialog uses available width when clamping narrow viewport', async () => {
  const { dialog, body } = await loadDialog({
    window: {
      innerHeight: 700,
      innerWidth: 400,
      getSelection: () => ({ toString: () => '' }),
    },
    chromeApi: {
      storage: { local: { get: async () => ({ manualCardPosition: { left: 5000, top: 40 } }), set: async () => {} } },
      runtime: { sendMessage: async () => ({ ok: true }) },
    },
  })

  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })

  assert.equal(body.children[0].style.width, 'min(440px, calc(100vw - 32px))')
  assert.equal(body.children[0].style.left, '32px')
})

test('manual card window disables save and cancel while saving', async () => {
  let finishSave
  const pendingSave = new Promise((resolve) => { finishSave = resolve })
  const { dialog, roots } = await loadDialog({ createSaver: () => ({ save: () => pendingSave }) })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  dialog.setStateForTest({
    a: { text: 'front', images: [], imageOrder: [] },
    b: { text: '', images: [], imageOrder: [] },
  })

  const savePromise = roots[0].current.props.onSave()
  const view = currentView(roots[0])
  assert.equal(findByRole(view, 'save').props.disabled, true)
  assert.equal(findByRole(view, 'cancel').props.disabled, true)

  finishSave({ id: 1 })
  await savePromise
})

test('manual card dialog preserves duplicate-card message for error code 40010', async () => {
  const { dialog, roots } = await loadDialog({
    createSaver: () => ({
      save: async () => {
        const error = new Error('卡片已存在')
        error.code = 40010
        throw error
      },
    }),
  })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  dialog.setStateForTest({
    a: { text: 'front', images: [], imageOrder: [] },
    b: { text: '', images: [], imageOrder: [] },
  })

  await roots[0].current.props.onSave()

  assert.equal(roots[0].current.props.state.error, '卡片已存在')
})

test('manual card dialog keeps empty-content and generic failure i18n keys', async () => {
  for (const [error, expected] of [
    [new Error('manualCard.emptyContent'), 'manualCard.emptyContent'],
    [new Error(), 'manualCard.saveFailed'],
  ]) {
    const { dialog, roots } = await loadDialog({ createSaver: () => ({ save: async () => { throw error } }) })
    await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
    await roots[0].current.props.onSave()
    assert.equal(roots[0].current.props.state.error, expected)
  }
})

test('manual card dialog saves before success notification and closes', async () => {
  const calls = []
  const messages = []
  const { dialog, body, roots } = await loadDialog({
    createSaver: () => ({ save: async () => { calls.push('save') } }),
    chromeApi: {
      storage: { local: { get: async () => ({}), set: async () => {} } },
      runtime: { sendMessage: async (message) => { calls.push('notify'); messages.push(message); return { ok: true } } },
    },
  })
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173', sourceTabId: 3 })
  dialog.setStateForTest({
    a: { text: 'front', images: [], imageOrder: [] },
    b: { text: '', images: [], imageOrder: [] },
  })

  await roots[0].current.props.onSave()

  assert.deepEqual(calls, ['save', 'notify'])
  assert.deepEqual(messages, [{
    type: 'OPENFLASH_NOTIFY_ACTIVE_TAB',
    message: 'manualCard.saved',
    level: 'success',
    sourceTabId: 3,
  }])
  assert.equal(roots[0].unmounted, true)
  assert.equal(body.children.length, 0)
})

test('manual card dialog close unmounts React root before removing host', async () => {
  const order = []
  const { dialog, body, roots } = await loadDialog()
  await dialog.open({ deckId: '7', baseUrl: 'http://localhost:5173' })
  roots[0].unmount = () => order.push('unmount')
  body.children[0].remove = () => order.push('remove')

  dialog.close()

  assert.deepEqual(order, ['unmount', 'remove'])
})
