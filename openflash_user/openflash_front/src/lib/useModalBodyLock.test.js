import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function createElement({ style = {}, scrollTop = 0 } = {}) {
  return {
    style: { ...style },
    scrollTop,
  }
}

function loadHookWithSynchronousEffect() {
  const source = readFileSync(new URL('./useModalBodyLock.js', import.meta.url), 'utf8')
  const executableSource = source
    .replace("import { useEffect } from 'react'\n", '')
    .replace('export default function useModalBodyLock', 'function useModalBodyLock')

  const moduleFactory = new Function(`
    let cleanup
    const useEffect = (callback) => {
      cleanup = callback()
    }

    ${executableSource}

    return {
      useModalBodyLock,
      cleanup: () => cleanup?.(),
    }
  `)

  return moduleFactory()
}

test('useModalBodyLock locks and restores the app root scroll container', () => {
  const body = createElement()
  const documentElement = createElement()
  const root = createElement({
    style: {
      overflowY: 'auto',
      overscrollBehavior: 'none',
    },
    scrollTop: 240,
  })
  const scrollCalls = []

  globalThis.document = {
    body,
    documentElement,
    getElementById: (id) => (id === 'root' ? root : null),
    addEventListener: () => {},
    removeEventListener: () => {},
  }
  globalThis.window = {
    scrollY: 0,
    addEventListener: () => {},
    removeEventListener: () => {},
    scrollTo: (...args) => scrollCalls.push(args),
  }

  const { useModalBodyLock, cleanup } = loadHookWithSynchronousEffect()

  useModalBodyLock(true)

  assert.equal(root.style.overflowY, 'hidden')
  assert.equal(root.style.overscrollBehavior, 'none')

  cleanup()

  assert.equal(root.style.overflowY, 'auto')
  assert.equal(root.style.overscrollBehavior, 'none')
  assert.equal(root.scrollTop, 240)
  assert.deepEqual(scrollCalls, [])

  delete globalThis.document
  delete globalThis.window
})

test('useModalBodyLock prevents touchmove from scrolling the page while locked', () => {
  const body = createElement()
  const documentElement = createElement()
  const root = createElement({
    style: {
      overflowY: 'auto',
      overscrollBehavior: 'none',
    },
    scrollTop: 240,
  })
  const documentListeners = new Map()

  globalThis.document = {
    body,
    documentElement,
    getElementById: (id) => (id === 'root' ? root : null),
    addEventListener: (type, listener, options) => {
      documentListeners.set(type, { listener, options })
    },
    removeEventListener: (type, listener) => {
      const current = documentListeners.get(type)
      if (current?.listener === listener) {
        documentListeners.delete(type)
      }
    },
  }
  globalThis.window = {
    scrollY: 0,
    addEventListener: () => {},
    removeEventListener: () => {},
    scrollTo: () => {},
  }

  const { useModalBodyLock, cleanup } = loadHookWithSynchronousEffect()

  useModalBodyLock(true)

  const touchmove = documentListeners.get('touchmove')
  assert.equal(typeof touchmove?.listener, 'function')
  assert.deepEqual(touchmove.options, { passive: false })

  let prevented = false
  touchmove.listener({
    touches: [{ clientY: 100 }],
    cancelable: true,
    target: root,
    preventDefault: () => { prevented = true },
  })

  assert.equal(prevented, true)

  cleanup()
  assert.equal(documentListeners.has('touchmove'), false)

  delete globalThis.document
  delete globalThis.window
})
