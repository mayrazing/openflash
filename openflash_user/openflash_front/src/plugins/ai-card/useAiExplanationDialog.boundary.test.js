import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const srcDir = join(__dirname, '..', '..')

// 读取源码做边界断言，防止 AI 打开流程回流到页面层。
function read(relativePath) {
  return readFileSync(join(srcDir, relativePath), 'utf8')
}

test('useAiExplanationDialog plugin owns AI explanation open flow dependencies', () => {
  const source = read('plugins/ai-card/useAiExplanationDialog.js')

  assert.match(source, /openAiCacheOrNotify/)
  assert.match(source, /checkAiCacheStatus/)
  assert.match(source, /UnauthorizedError/)
  assert.match(source, /isStale/)
})

test('pages do not own AI explanation open flow dependencies', () => {
  const files = [
    read('pages/DeckDetail.jsx'),
    read('pages/Practice.jsx'),
  ]

  for (const source of files) {
    assert.doesNotMatch(source, /ai-card:open/)
    assert.doesNotMatch(source, /openAiCacheOrNotify/)
    assert.doesNotMatch(source, /checkAiCacheStatus/)
    assert.doesNotMatch(source, /useAiExplanationDialog/)
  }
})

test('Ai action factories dispatch ai-card:open events', () => {
  const cardAction = read('plugins/ai-card/AiOpenAction.jsx')
  const practiceAction = read('plugins/ai-card/PracticeAiOpenAction.jsx')

  assert.match(cardAction, /ai-card:open/)
  assert.match(practiceAction, /ai-card:open/)
})

test('AiNotificationToast plugin dispatches ai-card:open on ready toast click', () => {
  const source = read('plugins/ai-card/AiNotificationToast.jsx')
  assert.match(source, /ai-card:open/)
  assert.doesNotMatch(source, /useAiExplanationDialog/)
  assert.doesNotMatch(source, /openAiCacheOrNotify/)
})
