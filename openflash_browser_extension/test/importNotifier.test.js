import assert from 'node:assert/strict'
import test from 'node:test'
import { badgeColors } from '../src/ui/appleColors.generated.js'

test('import notifier exposes a dependency-injected factory', async () => {
  let createImportNotifier
  try {
    ;({ createImportNotifier } = await import('../src/importNotifier.js'))
  } catch {
    // RED 阶段允许模块尚不存在，最终断言描述所需接口。
  }

  assert.equal(typeof createImportNotifier, 'function')
})

test('import notifier dispatches one result to storage, badge, and current page', async () => {
  const { createImportNotifier } = await import('../src/importNotifier.js')
  const statuses = []
  const badgeCalls = []
  const pageMessages = []
  const notify = createImportNotifier({
    setLastImportStatus: async (status) => statuses.push(status),
    action: {
      setBadgeText: async (value) => badgeCalls.push(['text', value]),
      setBadgeBackgroundColor: async (value) => badgeCalls.push(['color', value]),
    },
    tabs: {
      sendMessage: async (tabId, message) => pageMessages.push({ tabId, message }),
    },
    now: () => 123,
    setTimeout: () => 1,
    clearTimeout: () => {},
  })

  await notify('已保存', 'success', 7)

  assert.deepEqual(statuses, [{ message: '已保存', level: 'success', at: 123 }])
  assert.deepEqual(badgeCalls, [
    ['text', { text: '✓' }],
    ['color', { color: badgeColors.success }],
  ])
  assert.deepEqual(pageMessages, [{
    tabId: 7,
    message: { type: 'OPENFLASH_SHOW_NOTIFICATION', message: '已保存', level: 'success' },
  }])
})

for (const [level, color, text] of [
  ['success', badgeColors.success, '✓'],
  ['warning', badgeColors.warning, '!'],
  ['error', badgeColors.error, '!'],
]) {
  test(`import notifier uses Apple semantic ${level} badge color`, async () => {
    const { createImportNotifier } = await import('../src/importNotifier.js')
    const badgeCalls = []
    const notify = createImportNotifier({
      setLastImportStatus: async () => {},
      action: {
        setBadgeText: async (value) => badgeCalls.push(['text', value]),
        setBadgeBackgroundColor: async (value) => badgeCalls.push(['color', value]),
      },
      tabs: { sendMessage: async () => {} },
      now: () => 123,
      setTimeout: () => 1,
      clearTimeout: () => {},
    })

    await notify('导入结果', level)

    assert.deepEqual(badgeCalls, [
      ['text', { text }],
      ['color', { color }],
    ])
  })
}

test('import notifier falls back to error badge for an unknown level', async () => {
  const { createImportNotifier } = await import('../src/importNotifier.js')
  const badgeCalls = []
  const notify = createImportNotifier({
    setLastImportStatus: async () => {},
    action: {
      setBadgeText: async (value) => badgeCalls.push(['text', value]),
      setBadgeBackgroundColor: async (value) => badgeCalls.push(['color', value]),
    },
    tabs: { sendMessage: async () => {} },
    now: () => 123,
    setTimeout: () => 1,
    clearTimeout: () => {},
  })

  await notify('导入结果', 'unknown')

  assert.deepEqual(badgeCalls, [
    ['text', { text: '!' }],
    ['color', { color: badgeColors.error }],
  ])
})

test('page HUD delivery failure does not reject a completed import notification', async () => {
  const { createImportNotifier } = await import('../src/importNotifier.js')
  const notify = createImportNotifier({
    setLastImportStatus: async () => {},
    action: {
      setBadgeText: async () => {},
      setBadgeBackgroundColor: async () => {},
    },
    tabs: {
      sendMessage: async () => {
        throw new Error('Receiving end does not exist')
      },
    },
    now: () => 123,
    setTimeout: () => 1,
    clearTimeout: () => {},
  })

  await assert.doesNotReject(notify('已保存', 'success', 7))
})

test('import notifier reinstalls a missing page receiver and retries the HUD', async () => {
  const { createImportNotifier } = await import('../src/importNotifier.js')
  const calls = []
  let sendAttempts = 0
  const notify = createImportNotifier({
    setLastImportStatus: async () => {},
    action: {
      setBadgeText: async () => {},
      setBadgeBackgroundColor: async () => {},
    },
    tabs: {
      sendMessage: async (tabId, message) => {
        sendAttempts += 1
        calls.push(['send', tabId, message])
        if (sendAttempts === 1) throw new Error('Receiving end does not exist')
      },
    },
    ensurePageReceiver: async (tabId) => calls.push(['ensure', tabId]),
    now: () => 123,
    setTimeout: () => 1,
    clearTimeout: () => {},
  })

  await notify('已保存', 'success', 7)

  assert.deepEqual(calls, [
    ['send', 7, { type: 'OPENFLASH_SHOW_NOTIFICATION', message: '已保存', level: 'success' }],
    ['ensure', 7],
    ['send', 7, { type: 'OPENFLASH_SHOW_NOTIFICATION', message: '已保存', level: 'success' }],
  ])
})

test('storage failure does not block page HUD delivery', async () => {
  const { createImportNotifier } = await import('../src/importNotifier.js')
  const pageMessages = []
  const notify = createImportNotifier({
    setLastImportStatus: async () => {
      throw new Error('storage unavailable')
    },
    action: {
      setBadgeText: async () => {},
      setBadgeBackgroundColor: async () => {},
    },
    tabs: {
      sendMessage: async (tabId, message) => pageMessages.push({ tabId, message }),
    },
    now: () => 123,
    setTimeout: () => 1,
    clearTimeout: () => {},
  })

  await assert.doesNotReject(notify('已保存', 'success', 7))
  assert.equal(pageMessages.length, 1)
})

test('import notifier exposes an active-tab notification factory', async () => {
  const module = await import('../src/importNotifier.js')

  assert.equal(typeof module.createActiveTabNotifier, 'function')
})

test('active-tab notifier routes popup results through the shared notifier', async () => {
  const { createActiveTabNotifier } = await import('../src/importNotifier.js')
  const calls = []
  const notifyActiveTab = createActiveTabNotifier({
    tabs: {
      query: async (query) => {
        calls.push(['query', query])
        return [{ id: 7 }]
      },
    },
    notify: async (...args) => calls.push(['notify', ...args]),
  })

  await notifyActiveTab('请先登录', 'error')

  assert.deepEqual(calls, [
    ['query', { active: true, currentWindow: true }],
    ['notify', '请先登录', 'error', 7],
  ])
})

test('active-tab notifier sends manual-card results back to the source tab', async () => {
  const { createActiveTabNotifier } = await import('../src/importNotifier.js')
  const calls = []
  const notifyActiveTab = createActiveTabNotifier({
    tabs: {
      query: async () => {
        calls.push(['query'])
        return [{ id: 9 }]
      },
    },
    notify: async (...args) => calls.push(['notify', ...args]),
  })

  await notifyActiveTab('Saved', 'success', 3)

  assert.deepEqual(calls, [
    ['notify', 'Saved', 'success', 3],
  ])
})
