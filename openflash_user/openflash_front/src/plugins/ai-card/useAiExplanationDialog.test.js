import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

function read(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

function loadActionFactory(relativePath, exportName) {
  const source = read(relativePath)
    .replace(/^import .*$/gm, '')
    .replace(/export default function (\w+)/, 'function $1')
  return new Function(
    'withGenericClick',
    'window',
    'CustomEvent',
    `${source}; return ${exportName}`,
  )((fn) => fn, testWindow, TestCustomEvent)
}

const testWindow = {
  events: [],
  dispatchEvent(event) {
    this.events.push(event)
  },
}

class TestCustomEvent {
  constructor(type, init = {}) {
    this.type = type
    this.detail = init.detail
  }
}

test('Ai action factories ignore missing card payloads without dispatching', () => {
  const createAiOpenAction = loadActionFactory('AiOpenAction.jsx', 'createAiOpenAction')
  const createPracticeAiOpenAction = loadActionFactory('PracticeAiOpenAction.jsx', 'createPracticeAiOpenAction')

  testWindow.events = []

  assert.deepEqual(createAiOpenAction(), {})
  assert.deepEqual(createAiOpenAction({ card: {} }), {})
  assert.deepEqual(createPracticeAiOpenAction(), {})
  assert.deepEqual(createPracticeAiOpenAction({ card: {} }), {})
  assert.deepEqual(testWindow.events, [])
})

test('Ai action factories dispatch valid ai-card open events', () => {
  const createAiOpenAction = loadActionFactory('AiOpenAction.jsx', 'createAiOpenAction')
  const createPracticeAiOpenAction = loadActionFactory('PracticeAiOpenAction.jsx', 'createPracticeAiOpenAction')

  testWindow.events = []
  createAiOpenAction({ card: { id: 7, sideA: 'Word' }, title: 'Title' }).onOpen()
  createPracticeAiOpenAction({ card: { id: 8 }, side: 'b', text: 'Answer' }).onOpen()

  assert.deepEqual(testWindow.events.map(event => event.detail), [
    { cardId: 7, title: 'Title', deckId: undefined },
    { cardId: 8, side: 'b', title: 'Answer', deckId: undefined },
  ])
})

test('AiCardManager ignores ai-card open events without cardId', () => {
  const source = read('AiCardManager.jsx')

  assert.match(source, /event\.detail\s*\?\?\s*\{\}/)
  assert.match(source, /if\s*\(!cardId\)\s*return/)
  assert.match(source, /openAiCacheOrNotify/)
})

test('AiCardManager subscribes through core SSE without owning a connection', () => {
  const source = read('AiCardManager.jsx')

  assert.match(source, /useSse/)
  assert.match(source, /subscribe\(AI_READY_EVENT/)
  assert.doesNotMatch(source, /new EventSource/)
})
