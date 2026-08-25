import test from 'node:test'
import assert from 'node:assert/strict'
import { runPracticePrefetch } from './practicePrefetch.js'
import { invalidateDeckInstalledPlugins } from './deckInstalledPluginCache.js'

// 构造一个 stub 插件，slots['practice.prefetch'] 用函数形态。
function makePlugin(id, prefetchFn) {
  return { id, slots: { 'practice.prefetch': prefetchFn } }
}

test('runPracticePrefetch 并行调用所有注册了 practice.prefetch 的插件', async () => {
  const calls = []
  const plugins = [
    makePlugin('p1', async (deckId, context) => { calls.push(['p1', deckId, context.installedIds]) }),
    makePlugin('p2', async (deckId, context) => { calls.push(['p2', deckId, context.installedIds]) }),
    { id: 'p3', slots: {} }, // 无 prefetch
  ]
  await runPracticePrefetch(42, { plugins, installedIds: ['p1', 'p2'] })
  assert.deepEqual(calls.sort((a, b) => a[0].localeCompare(b[0])), [
    ['p1', 42, ['p1', 'p2']],
    ['p2', 42, ['p1', 'p2']],
  ])
})

test('runPracticePrefetch 只预热当前卡包已安装插件', async () => {
  const calls = []
  const plugins = [
    makePlugin('installed', async () => { calls.push('installed') }),
    makePlugin('not-installed', async () => { calls.push('not-installed') }),
  ]

  await runPracticePrefetch(42, { plugins, installedIds: ['installed'] })

  assert.deepEqual(calls, ['installed'])
})

test('runPracticePrefetch 同一 deckId 并发调用复用同一轮预热', async () => {
  let calls = 0
  let release
  const plugins = [
    makePlugin('p1', () => new Promise(resolve => {
      calls += 1
      release = resolve
    })),
  ]
  const first = runPracticePrefetch(42, { plugins, installedIds: ['p1'] })
  const second = runPracticePrefetch(42, { plugins, installedIds: ['p1'] })
  release()

  await Promise.all([first, second])

  assert.equal(calls, 1)
})

test('runPracticePrefetch 所有成功时整体 resolve', async () => {
  const plugins = [
    makePlugin('p1', async () => 'ok1'),
    makePlugin('p2', async () => 'ok2'),
  ]
  await runPracticePrefetch(1, { plugins, installedIds: ['p1', 'p2'] })
  // 走到这一行就算 resolve
  assert.ok(true)
})

test('runPracticePrefetch 单个 prefetch reject 不影响其它，整体仍 resolve', async () => {
  let p2Called = false
  const plugins = [
    makePlugin('p1', async () => { throw new Error('boom') }),
    makePlugin('p2', async () => { p2Called = true }),
  ]
  await runPracticePrefetch(1, { plugins, installedIds: ['p1', 'p2'] })
  assert.equal(p2Called, true)
})

test('runPracticePrefetch 单个 prefetch 超时不阻塞，整体仍 resolve', async () => {
  let p2Called = false
  const plugins = [
    makePlugin('p1', () => new Promise(() => {})), // 永不 resolve
    makePlugin('p2', async () => { p2Called = true }),
  ]
  const start = Date.now()
  await runPracticePrefetch(1, { plugins, installedIds: ['p1', 'p2'], timeoutMs: 50 })
  const elapsed = Date.now() - start
  assert.equal(p2Called, true)
  assert.ok(elapsed < 200, `应当在超时窗口左右返回，实际 ${elapsed}ms`)
})

test('runPracticePrefetch deckId 为 null 时直接 resolve，不调插件 prefetch', async () => {
  let called = false
  const plugins = [makePlugin('p1', async () => { called = true })]
  await runPracticePrefetch(null, { plugins })
  assert.equal(called, false)
})

test('runPracticePrefetch deckId 为 undefined 时直接 resolve，不调插件 prefetch', async () => {
  let called = false
  const plugins = [makePlugin('p1', async () => { called = true })]
  await runPracticePrefetch(undefined, { plugins })
  assert.equal(called, false)
})

test('runPracticePrefetch 同步抛错的 prefetch 被吞掉，不影响其它', async () => {
  let p2Called = false
  const plugins = [
    makePlugin('p1', () => { throw new Error('sync boom') }),
    makePlugin('p2', async () => { p2Called = true }),
  ]
  await runPracticePrefetch(1, { plugins, installedIds: ['p1', 'p2'] })
  assert.equal(p2Called, true)
})

test('runPracticePrefetch installedIds 加载超时按空安装列表降级，不卡练习启动', async () => {
  // 真实场景：/api/plugins/installed 卡死时，整轮 prefetch 不能无限等。
  // 用永挂的 fetch 模拟后端无响应，断言整轮在 timeoutMs 左右返回，
  // 且没有插件 prefetch 被调用（按"空安装列表"降级）。
  invalidateDeckInstalledPlugins(7777)
  const originalFetch = globalThis.fetch
  globalThis.fetch = () => new Promise(() => {})
  try {
    let called = false
    const plugins = [{ id: 'mask-mode', slots: { 'practice.prefetch': async () => { called = true } } }]
    const start = Date.now()
    await runPracticePrefetch(7777, { plugins, timeoutMs: 50 })
    const elapsed = Date.now() - start
    assert.equal(called, false)
    assert.ok(elapsed >= 40 && elapsed < 300, `应当在 timeoutMs 左右降级返回，实际 ${elapsed}ms`)
  } finally {
    globalThis.fetch = originalFetch
    invalidateDeckInstalledPlugins(7777)
  }
})
