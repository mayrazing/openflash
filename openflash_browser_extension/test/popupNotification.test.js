import assert from 'node:assert/strict'
import test from 'node:test'

test('popup notification exposes a runtime-backed factory', async () => {
  let createPopupNotifier
  try {
    ;({ createPopupNotifier } = await import('../src/popupNotification.js'))
  } catch {
    // RED 阶段允许模块尚不存在，最终断言描述所需接口。
  }

  assert.equal(typeof createPopupNotifier, 'function')
})

test('popup notifier sends user-facing results to the active-page route', async () => {
  const { createPopupNotifier } = await import('../src/popupNotification.js')
  const messages = []
  const notify = createPopupNotifier({
    sendMessage: async (message) => messages.push(message),
  })

  await notify('请先登录', 'error')

  assert.deepEqual(messages, [{
    type: 'OPENFLASH_NOTIFY_ACTIVE_TAB',
    message: '请先登录',
    level: 'error',
  }])
})

test('popup notification exposes a shared status presenter', async () => {
  const module = await import('../src/popupNotification.js')

  assert.equal(typeof module.createPopupStatusPresenter, 'function')
})

test('status presenter publishes every dynamic popup result', async () => {
  const { createPopupStatusPresenter } = await import('../src/popupNotification.js')
  const state = { error: '', aiSettingsError: '', message: '' }
  const notifications = []
  const presenter = createPopupStatusPresenter(state, async (...args) => notifications.push(args))

  await presenter.error('请先登录')
  await presenter.aiError('功能未开放')
  await presenter.success('已保存')

  assert.deepEqual(state, {
    error: '请先登录',
    aiSettingsError: '功能未开放',
    message: '已保存',
  })
  assert.deepEqual(notifications, [
    ['请先登录', 'error'],
    ['功能未开放', 'error'],
    ['已保存', 'success'],
  ])
})

test('status presenter keeps popup fallback when page HUD is unavailable', async () => {
  const { createPopupStatusPresenter } = await import('../src/popupNotification.js')
  const state = { error: '' }
  const presenter = createPopupStatusPresenter(state, async () => {
    throw new Error('No active page')
  })

  await assert.doesNotReject(presenter.error('当前页面不支持插件导入'))
  assert.equal(state.error, '当前页面不支持插件导入')
})
