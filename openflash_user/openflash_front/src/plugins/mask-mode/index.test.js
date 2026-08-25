/**
 * mask-mode prefetch 并行性回归测试。
 *
 * 现状：maskSettings 和 ttsSettings 必须并行读，不能串行多一个 RTT。
 * 静态扫描源码确保使用 Promise.allSettled（或 Promise.all）调度两次请求。
 */

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(__dirname, 'index.jsx'), 'utf8')

test('mask-mode prefetch 用 Promise.allSettled 并行读取 mask + tts 设置', () => {
  assert.match(source, /Promise\.allSettled\(\s*\[\s*getDeckMaskModeSettings/)
})

test('mask-mode prefetch 不再串行 await getDeckMaskModeSettings + getDeckTtsSettings', () => {
  // 串行写法的标志：单独 await getDeckTtsSettings(deckId)
  assert.doesNotMatch(source, /await\s+getDeckTtsSettings\(deckId\)/)
})
