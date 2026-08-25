import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  getCachedDeckInstalledPlugins,
  invalidateDeckInstalledPlugins,
  setCachedDeckInstalledPlugins,
} from './deckInstalledPluginCache.js'
import { loadDeckInstalledPlugins } from './deckInstalledPluginLoader.js'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test('loadDeckInstalledPlugins 合并同一 deckId 的并发请求并写共享缓存', async () => {
  invalidateDeckInstalledPlugins(7001)
  let fetchCount = 0
  globalThis.fetch = async () => {
    fetchCount += 1
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ code: 200, data: ['tts', 'mask-mode'] }),
    }
  }

  const [left, right] = await Promise.all([
    loadDeckInstalledPlugins(7001),
    loadDeckInstalledPlugins(7001),
  ])

  assert.deepEqual(left, ['tts', 'mask-mode'])
  assert.deepEqual(right, ['tts', 'mask-mode'])
  assert.equal(fetchCount, 1)
  assert.deepEqual(getCachedDeckInstalledPlugins(7001), ['tts', 'mask-mode'])
})

test('loadDeckInstalledPlugins 命中共享缓存时不请求后端', async () => {
  // 显式预置 cache，自包含，不依赖上一个 test 的执行顺序。
  invalidateDeckInstalledPlugins(7002)
  setCachedDeckInstalledPlugins(7002, ['tts', 'mask-mode'])
  let fetchCount = 0
  globalThis.fetch = async () => {
    fetchCount += 1
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ code: 200, data: [] }),
    }
  }

  const ids = await loadDeckInstalledPlugins(7002)

  assert.deepEqual(ids, ['tts', 'mask-mode'])
  assert.equal(fetchCount, 0)
})

test('loadDeckInstalledPlugins 在飞行中 invalidate 后，旧请求的结果不再写回缓存', async () => {
  // 真实场景：安装/卸载触发 invalidate 时，正在飞的旧请求若在之后才返回，
  // 旧 installedIds 不能覆盖新的安装状态——否则导致 PluginSlot 渲染过期内容。
  invalidateDeckInstalledPlugins(7003)
  let release
  globalThis.fetch = () => new Promise((resolve) => {
    release = () => resolve({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ code: 200, data: ['stale-plugin'] }),
    })
  })

  const inflightPromise = loadDeckInstalledPlugins(7003)
  // 安装/卸载触发：飞行中请求被作废
  invalidateDeckInstalledPlugins(7003)
  // 让旧请求结束
  release()
  await inflightPromise

  // 旧请求的 data 不应被写回 cache
  assert.equal(getCachedDeckInstalledPlugins(7003), undefined)
})
