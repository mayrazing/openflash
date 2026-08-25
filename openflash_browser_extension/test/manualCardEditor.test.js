import assert from 'node:assert'
import test from 'node:test'
import fs from 'node:fs'
import vm from 'node:vm'

function loadEditor() {
  const context = vm.createContext({ console, globalThis: {} })
  context.globalThis = context
  vm.runInContext(fs.readFileSync(new URL('../src/manualCardEditor.js', import.meta.url), 'utf8'), context)
  return context.OpenFlashManualCardEditor
}

test('manual card editor builds payload with image order', () => {
  const editor = loadEditor()
  const state = editor.createState('front')
  state.a.images.push({ id: 'a1' }, { id: 'a2' })
  state.a.imageOrder = ['a1', 'a2']
  state.b.text = 'back'
  state.b.images.push({ id: 'b1' })
  state.b.imageOrder = ['b1']

  assert.deepEqual(editor.buildPayload(state, {
    a1: '/uploads/a1.jpg',
    a2: '/uploads/a2.jpg',
    b1: '/uploads/b1.jpg',
  }), {
    sideA: 'front',
    sideAImage: ['/uploads/a1.jpg', '/uploads/a2.jpg'],
    sideB: 'back',
    sideBImage: ['/uploads/b1.jpg'],
  })
})

test('manual card editor detects empty and non-empty sides', () => {
  const editor = loadEditor()
  assert.equal(editor.hasAnyContent(editor.createState('')), false)
  const state = editor.createState('')
  state.b.images.push({ id: 'b1' })
  assert.equal(editor.hasAnyContent(state), true)
})

test('manual card editor extracts text and inline image order from editor DOM', () => {
  const editor = loadEditor()
  const imageChip = { dataset: { openflashImageId: 'a1' }, remove: () => {}, childNodes: [] }
  const editorNode = {
    childNodes: [
      { nodeType: 3, textContent: 'front ' },
      imageChip,
      { nodeType: 3, textContent: ' back' },
    ],
    querySelectorAll: () => [imageChip],
  }

  assert.deepEqual(editor.readSideFromEditor(editorNode), {
    text: 'front \uE000OFIMG:0\uE000 back',
    imageOrder: ['a1'],
  })
})

test('manual card editor preserves line breaks when extracting text', () => {
  const editor = loadEditor()
  const editorNode = {
    childNodes: [
      { nodeType: 3, textContent: 'line one' },
      { nodeType: 1, tagName: 'BR', dataset: {}, childNodes: [] },
      { nodeType: 3, textContent: 'line two' },
    ],
    querySelectorAll: () => [],
  }

  assert.deepEqual(editor.readSideFromEditor(editorNode), {
    text: 'line one\nline two',
    imageOrder: [],
  })
})
