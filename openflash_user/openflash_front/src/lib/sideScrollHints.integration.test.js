import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const srcDir = join(__dirname, '..')

// 读取页面源码，验证页面把右侧快捷滚动接到用户能点到的外层区域。
function readSource(relativePath) {
  return readFileSync(join(srcDir, relativePath), 'utf8')
}

test('Home uses shared side scroll hints and scrolls the root container to top and bottom', () => {
  const source = readSource('pages/Home.jsx')

  assert.match(source, /useSideScrollHints/)
  assert.match(source, /sideScrollHandlers/)
  assert.match(source, /<AppPage[\s\S]*\{\.\.\.sideScrollHandlers\}/)
  assert.match(source, /root\.scrollTo\(\{ top: 0, behavior: 'smooth' \}\)/)
  assert.match(source, /root\.scrollTo\(\{ top: root\.scrollHeight, behavior: 'smooth' \}\)/)
})

test('DeckDetail reuses shared side scroll hints while keeping autoLoadToBottom for bottom clicks', () => {
  const source = readSource('pages/DeckDetail.jsx')

  assert.match(source, /useSideScrollHints/)
  assert.match(source, /sideScrollHandlers/)
  assert.match(source, /<AppPage[\s\S]*\{\.\.\.sideScrollHandlers\}/)
  assert.match(source, /onBottom: autoLoadToBottom/)
  assert.doesNotMatch(source, /function getSideHintFromPointer/)
  assert.doesNotMatch(source, /function sideHintClass/)
})
