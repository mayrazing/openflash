import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test, { after } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { createServiceUrlHandlers } from '../src/popup/serviceUrlHandlers.js'

const server = await createServer({
  root: new URL('..', import.meta.url).pathname,
  configFile: false,
  appType: 'custom',
  esbuild: { jsx: 'automatic', jsxImportSource: 'react' },
  optimizeDeps: { noDiscovery: true },
  server: { middlewareMode: true, hmr: false, ws: false },
})

after(() => server.close())

const { default: PopupView } = await server.ssrLoadModule('/src/popup/PopupView.jsx')
const { createDeckActionsClick } = await server.ssrLoadModule('/src/popup/deckActions.js')
const source = readFileSync(new URL('../src/popup/PopupView.jsx', import.meta.url), 'utf8')
const css = readFileSync(new URL('../src/ui/app.css', import.meta.url), 'utf8')

const labels = {
  'popup.title': 'OpenFlash Import',
  'popup.serviceUrl': 'Service URL',
  'popup.login': 'Log in',
  'popup.refresh': 'Refresh',
  'popup.logout': 'Log out',
  'popup.decksTitle': 'Decks',
  'popup.deckName': 'Deck name',
  'popup.createDeck': 'Create',
  'popup.unsetDefaultDeck': 'Unset default',
  'popup.setDefaultDeck': 'Set default',
  'popup.deleteDeck': 'Delete',
  'popup.deleteDeckConfirmTitle': 'Delete deck',
  'popup.deleteDeckConfirmBody': 'Delete "{{deckName}}"? Its cards are deleted with it and cannot be restored.',
  'popup.cancel': 'Cancel',
  'popup.selectedDeck': 'Selected',
  'popup.defaultDeckSet': 'Default deck set',
  'popup.defaultDeckMissing': 'No default deck',
  'popup.shortcutsTitle': 'Shortcuts',
  'popup.shortcutImportDefault': 'Import selection directly',
  'popup.shortcutManualCard': 'Quick manual card',
  'popup.shortcutBrowserTip': 'Change shortcuts in browser settings',
  'popup.shortcutSettingsButton': 'Set shortcuts',
  'popup.shortcutSettingsAction': 'Set',
  'popup.aiPromptTitle': 'AI prompt',
  'popup.aiPromptTitleWithDeck': '{{title}} · {{deckName}}',
  'popup.aiPluginRequired': 'AI plugin required',
  'popup.aiPluginDisabled': 'Plugin disabled',
  'popup.aiCompletionPrompt': 'Other side completion prompt',
  'popup.aiCompletionPlaceholder': 'Prompt example',
  'popup.aiCompletionEnabled': 'Enable automatic completion',
  'popup.saveAi': 'Save prompt',
}

function translate(key, params = {}) {
  return Object.entries(params).reduce(
    (text, [name, value]) => text.replaceAll(`{{${name}}}`, String(value)),
    labels[key] ?? key,
  )
}

const actions = {
  setServiceUrl() {},
  login() {},
  refresh() {},
  logout() {},
  createDeck() {},
  selectDeck() {},
  toggleDefaultDeck() {},
  requestDeleteDeck() {},
  cancelDeleteDeck() {},
  confirmDeleteDeck() {},
  openShortcutSettings() {},
  setAiCompletionPrompt() {},
  setAiCompletionEnabled() {},
  saveAiSettings() {},
}

function render(state) {
  return renderToStaticMarkup(createElement(PopupView, {
    state,
    actions,
    t: translate,
    shortcutText: (commandName) => commandName === 'openflash-import-default' ? 'Alt+Shift+D' : 'Not set',
  }))
}

function renderLoggedIn(nickname = 'Alice', overrides = {}) {
  return render({
    serviceUrl: 'http://localhost:8080',
    user: { nickname },
    decks: [
      { id: 1, name: 'Spanish' },
      { id: 2, name: 'Finnish' },
    ],
    selectedDeckId: '1',
    defaultDeckId: '1',
    aiSettings: {
      aiCompletionEnabled: true,
      aiCompletionPrompt: 'Explain simply',
    },
    aiSettingsError: '',
    lastImportStatus: { level: 'success', message: 'Imported' },
    message: 'Saved',
    error: '',
    ...overrides,
  })
}

for (const [level, role, message] of [
  ['success', 'status', 'Imported'],
  ['warning', 'alert', 'Imported with warning'],
  ['error', 'alert', 'Import failed'],
]) {
  test(`popup renders persisted ${level} notification`, () => {
    const html = renderLoggedIn('Alice', {
      lastImportStatus: { level, message },
      message: '',
      error: '',
    })

    assert.match(html, new RegExp(`<p[^>]*role="${role}"[^>]*>${message}</p>`))
  })
}

test('logged-out popup renders accessible service, login, refresh, and error controls', () => {
  const html = render({
    serviceUrl: 'http://localhost:8080',
    user: null,
    error: 'Session unavailable',
  })

  assert.match(html, /<input[^>]*value="http:\/\/localhost:8080"/)
  assert.match(html, /<input[^>]*aria-label="Service URL"/)
  assert.match(html, />Service URL</)
  assert.match(html, /<button[^>]*>Log in<\/button>/)
  assert.match(html, /<button[^>]*>Refresh<\/button>/)
  assert.match(html, /role="alert"[^>]*>Session unavailable</)
})

test('logged-in popup exposes deck, shortcut, and AI settings behavior as accessible HTML', () => {
  const html = renderLoggedIn()

  for (const text of [
    'Alice',
    'Log out',
    'Decks',
    'Deck name',
    'Create',
    'Spanish',
    'Finnish',
    'Unset default',
    'Set default',
    'Delete',
    'Import selection directly',
    'Quick manual card',
    'Not set',
    'Other side completion prompt',
    'Enable automatic completion',
    'Save prompt',
  ]) {
    assert.match(html, new RegExp(text))
  }
  assert.match(html, /<kbd[^>]*>Alt\+Shift\+D<\/kbd>/)
  assert.match(html, /<span[^>]*class="status"[^>]*>Not set<\/span>/)
  assert.match(html, /Default deck set/)
  assert.match(html, /class="[^"]*primary-button[^"]*"[^>]*>Create<\/button>/)
  assert.match(html, /role="status"[^>]*>Saved</)
})

test('logged-in popup reuses the approved mock DOM structure instead of approximating it with component defaults', () => {
  const html = renderLoggedIn()

  for (const className of [
    'popup',
    'navbar',
    'identity',
    'content',
    'panel',
    'panel-body',
    'panel-heading',
    'field-label',
    'input',
    'create-row',
    'deck-list',
    'deck-row',
    'deck-main',
    'deck-actions',
    'list-row',
    'textarea',
    'toggle',
    'full-button',
  ]) {
    assert.match(html, new RegExp(`class="[^"]*\\b${className}\\b`), `缺少 Mock 结构类 ${className}`)
  }
})

test('popup stylesheet keeps the approved mock dimensions and spacing as literal source of truth', () => {
  for (const rule of [
    /html,\s*\nbody\s*\{[\s\S]*?height:\s*600px;[\s\S]*?overflow:\s*hidden;/,
    /body\.popup-page\s*\{[\s\S]*?width:\s*380px;/,
    /\.popup\s*\{[\s\S]*?width:\s*380px;[\s\S]*?height:\s*100%;[\s\S]*?overflow-y:\s*auto;/,
    /\.navbar\s*\{[\s\S]*?min-height:\s*64px;[\s\S]*?padding:\s*14px 16px;/,
    /\.content\s*\{[\s\S]*?gap:\s*10px;[\s\S]*?padding:\s*12px;/,
    /\.panel\s*\{[\s\S]*?border-radius:\s*16px;/,
    /\.input\s*\{[\s\S]*?height:\s*38px;[\s\S]*?border-radius:\s*10px;/,
    /\.deck-row\s*\{[\s\S]*?border-radius:\s*12px;/,
    /\.list-row\s*\{[\s\S]*?min-height:\s*44px;[\s\S]*?padding:\s*0 12px;/,
  ]) {
    assert.match(css, rule)
  }
})

test('popup App fixes its rendered width to 380px outside class ordering', () => {
  assert.match(
    renderLoggedIn(),
    /<div[^>]*class="[^"]*k-app[^"]*"[^>]*style="[^"]*width:380px[^"]*"/,
  )
})

test('logged-in popup truncates a long account name while preserving its accessible full value', () => {
  const nickname = 'e2e-user-with-a-production-length-account-name@example.com'
  const html = renderLoggedIn(nickname)
  const account = html.match(new RegExp(`<span[^>]*>${nickname}</span>`))?.[0] || ''

  assert.match(account, /class="account"/)
  assert.match(account, new RegExp(`aria-label="${nickname}"`))
  assert.match(account, new RegExp(`title="${nickname}"`))
})

test('logged-in popup renders a compact identity header with the account below the title', () => {
  const html = renderLoggedIn()
  const header = html.match(/<header[^>]*data-testid="popup-header"[^>]*>[\s\S]*?<\/header>/)?.[0] || ''

  assert.match(header, /<h1[^>]*>OpenFlash Import<\/h1>[\s\S]*?<span[^>]*>Alice<\/span>/)
  assert.match(header, /<button[^>]*>Log out<\/button>/)
})

test('create deck button uses the approved mock primary button', () => {
  assert.match(
    renderLoggedIn(),
    /<button[^>]*class="[^"]*primary-button[^"]*"[^>]*>Create<\/button>/,
  )
})

test('delete deck button uses a compact clear danger treatment instead of a filled danger block', () => {
  const deleteButton = renderLoggedIn().match(/<button[^>]*>Delete<\/button>/)?.[0] || ''

  assert.match(deleteButton, /class="[^"]*text-button[^"]*danger[^"]*"/)
  assert.doesNotMatch(deleteButton, /bg-app-danger-fill/)
  assert.doesNotMatch(deleteButton, /background-color:var\(--app-danger-fill\)/)
})

test('delete deck button only requests confirmation instead of deleting straight away', () => {
  assert.match(source, /onClick=\{\(\) => actions\.requestDeleteDeck\(deck\.id\)\}/)
  assert.doesNotMatch(source, /actions\.deleteDeck/)
})

test('popup renders no delete confirmation while no deck deletion is pending', () => {
  assert.doesNotMatch(renderLoggedIn(), /alertdialog/)
})

test('pending deck deletion renders an alert dialog naming the deck with cancel and delete choices', () => {
  const html = renderLoggedIn('Alice', { pendingDeleteDeckId: '2' })
  const dialog = html.match(/<div[^>]*role="alertdialog"[^>]*>[\s\S]*?<\/div>\s*<\/div>/)?.[0] || ''

  assert.match(dialog, /Delete deck/)
  assert.match(dialog, /Delete &quot;Finnish&quot;\? Its cards are deleted with it and cannot be restored\./)
  assert.match(dialog, /<button[^>]*>Cancel<\/button>/)
  assert.match(dialog, /<button[^>]*class="[^"]*text-button[^"]*danger[^"]*"[^>]*>Delete<\/button>/)
  assert.match(dialog, /aria-labelledby="[^"]+"/)
})

test('delete confirmation stays hidden when the pending deck is already gone from the list', () => {
  assert.doesNotMatch(renderLoggedIn('Alice', { pendingDeleteDeckId: '404' }), /alertdialog/)
})

test('delete confirmation overlays the whole popup instead of pushing the layout down', () => {
  assert.match(css, /\.dialog-backdrop\s*\{[\s\S]*?position:\s*fixed;[\s\S]*?inset:\s*0;/)
  assert.match(css, /\.dialog\s*\{[\s\S]*?border-radius:\s*14px;/)
})

test('each deck keeps selection content and actions in separate stacked regions', () => {
  const html = renderLoggedIn()
  const row = html.match(/<article[^>]*data-testid="deck-row-1"[^>]*>[\s\S]*?<\/article>/)?.[0] || ''

  assert.match(row, /data-testid="deck-main-1"/)
  assert.match(row, /data-testid="deck-actions-1"/)
  assert.match(row, /aria-current="true"/)
  assert.match(row, /Selected/)
  assert.ok(row.indexOf('data-testid="deck-main-1"') < row.indexOf('data-testid="deck-actions-1"'))
})

test('deck action bar selects the deck on blank clicks and stays out of the way of its buttons', () => {
  const selected = []
  const onClick = createDeckActionsClick((deckId) => selected.push(deckId), 7)

  onClick({ target: { closest: (selector) => selector === 'button' ? null : null } })
  assert.deepEqual(selected, [7], '动作条空白点击应选中该卡包')

  onClick({ target: { closest: (selector) => selector === 'button' ? { tagName: 'BUTTON' } : null } })
  assert.deepEqual(selected, [7], '按钮点击不得触发选中, 保持原按钮行为')
})

test('deck action bar wires its blank-click handler in the rendered row', () => {
  assert.match(source, /className="deck-actions"[\s\S]*?onClick=\{createDeckActionsClick\(actions\.selectDeck, deck\.id\)\}/)
  assert.match(css, /\.deck-actions\s*\{[\s\S]*?cursor:\s*pointer;/)
})

test('selected deck renders both aria-current and a visible translated marker', () => {
  assert.match(
    renderLoggedIn(),
    /<button[^>]*aria-current="true"[^>]*>[\s\S]*?Spanish[\s\S]*?Selected[\s\S]*?<\/button>/,
  )
})

test('service URL handlers persist once on input and once on blur without onChange', () => {
  assert.equal(typeof createServiceUrlHandlers, 'function')

  const serviceInputSource = source.match(
    /<input\s+aria-label=\{t\('popup\.serviceUrl'\)\}[\s\S]*?\/>/,
  )?.[0] || ''
  assert.match(serviceInputSource, /\{\.\.\.serviceUrlHandlers\}/)
  assert.doesNotMatch(serviceInputSource, /\bonChange=/)

  const savedValues = []
  const handlers = createServiceUrlHandlers((value) => savedValues.push(value))

  handlers.onInput({ currentTarget: { value: 'http://typing' } })
  assert.deepEqual(savedValues, ['http://typing'])

  handlers.onBlur({ currentTarget: { value: 'http://final' } })
  assert.deepEqual(savedValues, ['http://typing', 'http://final'])
  assert.equal(handlers.onChange, undefined)
})

test('AI controls are disabled when AI settings failed to load', () => {
  const html = render({
    serviceUrl: 'http://localhost:8080',
    user: { username: 'alice' },
    decks: [],
    selectedDeckId: null,
    defaultDeckId: null,
    aiSettings: { aiCompletionEnabled: false, aiCompletionPrompt: null },
    aiSettingsError: 'AI unavailable',
    lastImportStatus: null,
    message: '',
    error: '',
  })

  assert.match(html, /role="alert"[^>]*title="AI unavailable"[^>]*>Plugin disabled</)
  assert.match(html, /<textarea[^>]*disabled=""/)
  assert.match(html, /<textarea[^>]*aria-label="Other side completion prompt"/)
  assert.match(html, /<button[^>]*class="[^"]*toggle[^"]*"[^>]*disabled=""/)
  assert.match(html, /<button[^>]*disabled=""[^>]*>Save prompt<\/button>/)
})

test('PopupView keeps the Konsta iOS app shell without letting component defaults replace the mock layout', () => {
  assert.match(source, /from 'konsta\/react'/)
  assert.match(source, /\bApp\b/)
  for (const component of ['Button', 'Card', 'List', 'ListItem', 'ListInput', 'Navbar', 'Toggle']) {
    assert.doesNotMatch(source, new RegExp(`\\b${component}\\b`))
  }
})
