import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function loadHook() {
  const source = readFileSync(new URL('./usePromptEditDialog.js', import.meta.url), 'utf8')
  const executableSource = source
    .replace("import { useState } from 'react'\n", '')
    .replace('export default function usePromptEditDialog', 'function usePromptEditDialog')

  const states = []
  let callIdx = 0

  function useState(initial) {
    const idx = callIdx++
    if (states[idx] === undefined) states[idx] = initial
    const setValue = (v) => {
      states[idx] = typeof v === 'function' ? v(states[idx]) : v
    }
    return [states[idx], setValue]
  }

  const factory = new Function('useState', `${executableSource}; return usePromptEditDialog`)
  const rawHook = factory(useState)

  // reset callIdx each "render" to simulate React re-render
  return (committedValue, onSave) => {
    callIdx = 0
    return rawHook(committedValue, onSave)
  }
}

test('初始状态 isOpen=false，draft 等于传入值', () => {
  const hook = loadHook()
  const { isOpen, draft } = hook('初始提示词', () => {})
  assert.equal(isOpen, false)
  assert.equal(draft, '初始提示词')
})

test('open() 后 isOpen=true', () => {
  const hook = loadHook()
  const r1 = hook('初始提示词', () => {})
  r1.open()
  const r2 = hook('初始提示词', () => {})
  assert.equal(r2.isOpen, true)
})

test('setDraft 更新 draft', () => {
  const hook = loadHook()
  const r1 = hook('初始提示词', () => {})
  r1.open()
  const r2 = hook('初始提示词', () => {})
  r2.setDraft('新内容')
  const r3 = hook('初始提示词', () => {})
  assert.equal(r3.draft, '新内容')
})

test('confirm() 调用 onSave 并关闭', () => {
  const saves = []
  const hook = loadHook()
  const r1 = hook('初始提示词', (v) => saves.push(v))
  r1.open()
  const r2 = hook('初始提示词', (v) => saves.push(v))
  r2.setDraft('修改后')
  const r3 = hook('初始提示词', (v) => saves.push(v))
  r3.confirm()
  const r4 = hook('初始提示词', (v) => saves.push(v))
  assert.equal(r4.isOpen, false)
  assert.deepEqual(saves, ['修改后'])
})

test('cancel() 关闭不调用 onSave，draft 重置', () => {
  const saves = []
  const hook = loadHook()
  const r1 = hook('初始提示词', (v) => saves.push(v))
  r1.open()
  const r2 = hook('初始提示词', (v) => saves.push(v))
  r2.setDraft('修改后')
  const r3 = hook('初始提示词', (v) => saves.push(v))
  r3.cancel()
  const r4 = hook('初始提示词', (v) => saves.push(v))
  assert.equal(r4.isOpen, false)
  assert.equal(r4.draft, '初始提示词')
  assert.deepEqual(saves, [])
})
