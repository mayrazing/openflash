import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

/**
 * 直接验证 zh/en 两份 locale 文件包含 mask-mode 模式说明的目标文案。
 * 这里读的是真实运行时数据源（locales/*.json），不是源码字面量，保持文案约束。
 */
test('mask-mode locales describe random mode as whole-face mask on selected questions', async () => {
  const zh = await readFile(new URL('../../locales/zh.json', import.meta.url), 'utf8')
  const en = await readFile(new URL('../../locales/en.json', import.meta.url), 'utf8')

  assert.match(zh, /随机选择部分题目整面遮蔽/)
  assert.doesNotMatch(zh, /随机遮蔽只遮一部分/)
  assert.match(en, /randomly selects some questions to mask the whole face/i)
  assert.doesNotMatch(en, /Random masks only part/)
})
