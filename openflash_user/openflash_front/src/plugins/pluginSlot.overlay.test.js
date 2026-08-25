import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const slotSource = readFileSync(join(__dirname, 'pluginSlot.jsx'), 'utf8')
const maskOverlay = readFileSync(join(__dirname, 'mask-mode/QuestionFaceMaskOverlay.jsx'), 'utf8')
const practiceCard = readFileSync(join(__dirname, '../components/PracticeCard.jsx'), 'utf8')

test('PluginSlot wrapper keeps normal plugin UI clickable by default', () => {
  // 通用插槽不能默认 pointer-events-none，否则设置页插件按钮、卡片动作按钮都会点不到。
  assert.doesNotMatch(slotSource, /className=\{[^}]*pointer-events-none/)
  assert.doesNotMatch(slotSource, /const\s+mergedClassName/)
})

test('PracticeCard overlay slot opts into pointer-events-none at call site', () => {
  // 只有题目面覆盖层需要外层不捕获 pointer，避免 overlay 组件返 null 时吞掉父容器点击。
  assert.match(practiceCard, /slotName="practice\.question-face\.overlay"[\s\S]*className="absolute inset-0 z-10 pointer-events-none"/)
})

test('QuestionFaceMaskOverlay claims pointer events when masked/transparent', () => {
  // overlay 真正接管时必须加 pointer-events-auto，配合调用点的 pointer-events-none 外层。
  assert.match(maskOverlay, /pointer-events-auto/)
})

test('PracticeCard overlay slot no longer stops propagation at container level', () => {
  // 外层 pointer-events-none 后，无需再在 containerProps 上 stopPropagation，
  // 由 overlay 组件自己决定何时 stopPropagation。
  assert.doesNotMatch(practiceCard, /containerProps=\{\s*\{[\s\S]*?stopOverlayPropagation/)
})
