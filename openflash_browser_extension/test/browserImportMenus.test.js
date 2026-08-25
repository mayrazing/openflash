import assert from 'node:assert/strict'
import test from 'node:test'
import { ROOT_MENU_ID } from '../src/config.js'
import {
  createBrowserImportMenus,
  getDeckIdForMenuClick,
} from '../src/browserImportMenus.js'
import { resetLanguage, setLanguage } from '../src/i18n.js'

const decks = [
  { id: 1, name: 'A' },
  { id: 2, name: 'B' },
]

function makeDeps({ defaultDeckId = null } = {}) {
  const created = []
  const imports = []
  const notifications = []
  const clearedDefaults = []
  return {
    created,
    imports,
    notifications,
    clearedDefaults,
    contextMenus: {
      async removeAll() {},
      create(item) {
        created.push(item)
      },
    },
    storage: {
      async getDefaultDeckId() {
        return defaultDeckId
      },
      async setDefaultDeckId(deckId) {
        clearedDefaults.push(deckId)
      },
    },
    api: {
      async decks() {
        return decks
      },
    },
    async getServiceUrl() {
      return 'http://openflash.test'
    },
    async importSelectionToDeck(tab, deckId) {
      imports.push({ tab, deckId })
    },
    async notify(message, level, tabId) {
      notifications.push({ message, level, tabId })
    },
  }
}

test('refreshMenus creates only root menu when default exists', async () => {
  const deps = makeDeps({ defaultDeckId: '2' })
  const menus = createBrowserImportMenus(deps)

  await menus.refreshMenus()

  assert.equal(menus.currentDefaultDeckId(), '2')
  assert.deepEqual(deps.created, [
    {
      id: ROOT_MENU_ID,
      title: 'OpenFlash Import',
      contexts: ['all'],
    },
  ])
})

test('refreshMenus creates only root menu when default is missing', async () => {
  const deps = makeDeps()
  const menus = createBrowserImportMenus(deps)

  await menus.refreshMenus()

  assert.equal(menus.currentDefaultDeckId(), null)
  assert.deepEqual(deps.created, [
    {
      id: ROOT_MENU_ID,
      title: 'OpenFlash Import',
      contexts: ['all'],
    },
  ])
})

test('refreshMenus clears invalid default deck', async () => {
  const deps = makeDeps({ defaultDeckId: '9' })
  const menus = createBrowserImportMenus(deps)

  await menus.refreshMenus()

  assert.equal(menus.currentDefaultDeckId(), null)
  assert.deepEqual(deps.clearedDefaults, [null])
})

test('refreshMenus clears current default deck when deck loading fails', async () => {
  const deps = makeDeps({ defaultDeckId: '2' })
  const menus = createBrowserImportMenus(deps)

  await menus.refreshMenus()
  assert.equal(menus.currentDefaultDeckId(), '2')

  deps.api.decks = async () => {
    throw new Error('加载失败')
  }

  await assert.rejects(menus.refreshMenus(), /加载失败/)
  assert.equal(menus.currentDefaultDeckId(), null)
})

test('refreshMenus reruns when called while refresh is in flight', async () => {
  let releaseFirstDeckLoad
  let defaultDeckId = '2'
  let deckLoadCount = 0
  const deps = makeDeps({ defaultDeckId: '2' })
  deps.storage.getDefaultDeckId = async () => defaultDeckId
  deps.api.decks = async () => {
    deckLoadCount += 1
    if (deckLoadCount === 1) {
      await new Promise((resolve) => {
        releaseFirstDeckLoad = resolve
      })
      defaultDeckId = '1'
    }
    return decks
  }
  const menus = createBrowserImportMenus(deps)

  const firstRefresh = menus.refreshMenus()
  await new Promise((resolve) => setImmediate(resolve))
  const secondRefresh = menus.refreshMenus()
  releaseFirstDeckLoad()
  await secondRefresh
  await firstRefresh

  assert.equal(menus.currentDefaultDeckId(), '1')
  assert.equal(deckLoadCount, 2)
})

test('refreshMenus reruns pending refresh after first load failure', async () => {
  let releaseFirstDeckLoad
  let defaultDeckId = '2'
  let deckLoadCount = 0
  const deps = makeDeps({ defaultDeckId: '2' })
  deps.storage.getDefaultDeckId = async () => defaultDeckId
  const menus = createBrowserImportMenus(deps)

  await menus.refreshMenus()
  assert.equal(menus.currentDefaultDeckId(), '2')

  deps.api.decks = async () => {
    deckLoadCount += 1
    if (deckLoadCount === 1) {
      await new Promise((resolve) => {
        releaseFirstDeckLoad = resolve
      })
      throw new Error('加载失败')
    }
    return decks
  }

  const firstRefresh = menus.refreshMenus()
  await new Promise((resolve) => setImmediate(resolve))
  defaultDeckId = '1'
  const secondRefresh = menus.refreshMenus()
  releaseFirstDeckLoad()
  await secondRefresh
  await firstRefresh

  assert.equal(menus.currentDefaultDeckId(), '1')
  assert.equal(deckLoadCount, 2)
})

test('handleMenuClick imports to default deck for root menu click', async () => {
  const deps = makeDeps({ defaultDeckId: '2' })
  const menus = createBrowserImportMenus(deps)
  await menus.refreshMenus()

  await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, { id: 7 })

  assert.deepEqual(deps.imports, [{ tab: { id: 7 }, deckId: '2' }])
})

test('handleMenuClick imports root default deck from storage after worker restart', async () => {
  const deps = makeDeps({ defaultDeckId: '2' })
  const menus = createBrowserImportMenus(deps)

  await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, { id: 7 })

  assert.equal(menus.currentDefaultDeckId(), '2')
  assert.deepEqual(deps.imports, [{ tab: { id: 7 }, deckId: '2' }])
})

test('handleMenuClick clears invalid stored default deck after worker restart', async () => {
  const deps = makeDeps({ defaultDeckId: '9' })
  const menus = createBrowserImportMenus(deps)

  await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, { id: 7 })

  assert.equal(menus.currentDefaultDeckId(), null)
  assert.deepEqual(deps.imports, [])
  assert.deepEqual(deps.clearedDefaults, [null])
  assert.deepEqual(deps.notifications, [
    { message: 'Default deck unavailable. Please set it again.', level: 'warning', tabId: 7 },
  ])
})

test('handleMenuClick warns without default deck for root menu click', async () => {
  const deps = makeDeps()
  const menus = createBrowserImportMenus(deps)
  await menus.refreshMenus()

  await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, { id: 7 })

  assert.deepEqual(deps.imports, [])
  assert.deepEqual(deps.notifications, [
    { message: 'Set a default deck in the extension settings first.', level: 'warning', tabId: 7 },
  ])
})

test('handleMenuClick notifies when target tab is missing', async () => {
  const deps = makeDeps({ defaultDeckId: '2' })
  const menus = createBrowserImportMenus(deps)
  await menus.refreshMenus()

  await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, null)

  assert.deepEqual(deps.notifications, [{ message: 'No available page', level: 'warning', tabId: undefined }])
})

test('getDeckIdForMenuClick resolves menu ids', () => {
  assert.equal(getDeckIdForMenuClick(ROOT_MENU_ID, '2'), '2')
  assert.equal(getDeckIdForMenuClick(ROOT_MENU_ID, null), null)
  assert.equal(getDeckIdForMenuClick('unknown-menu', '2'), null)
})

test('refreshMenus uses current language for root menu title', async () => {
  resetLanguage()
  try {
    setLanguage('zh')
    const deps = makeDeps()
    const menus = createBrowserImportMenus(deps)

    await menus.refreshMenus()

    assert.equal(deps.created[0].title, 'OpenFlash 导入')
  } finally {
    resetLanguage()
  }
})

test('handleMenuClick warnings use current language', async () => {
  resetLanguage()
  try {
    const deps = makeDeps()
    const menus = createBrowserImportMenus(deps)
    await menus.refreshMenus()

    await menus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, { id: 7 })

    assert.deepEqual(deps.notifications, [
      { message: 'Set a default deck in the extension settings first.', level: 'warning', tabId: 7 },
    ])
  } finally {
    resetLanguage()
  }
})
