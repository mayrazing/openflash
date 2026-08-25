import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

class FakeElement {
  constructor({
    tagName = 'DIV',
    parentElement = null,
    style = {},
    scrollTop = 0,
    scrollHeight = 100,
    clientHeight = 100,
    disabled = false,
    role = null,
    pointerActivation = false,
  } = {}) {
    this.tagName = tagName
    this.parentElement = parentElement
    this.style = { ...style }
    this.scrollTop = scrollTop
    this.scrollHeight = scrollHeight
    this.clientHeight = clientHeight
    this.disabled = disabled
    this.role = role
    this.pointerActivation = pointerActivation
  }

  getAttribute(name) {
    if (name === 'role') return this.role
    if (name === 'aria-disabled') return null
    if (name === 'data-pointer-activation') return this.pointerActivation ? '' : null
    return null
  }

  matches(selector) {
    return selector.split(',').some(part => {
      const item = part.trim()
      if (item === 'button') return this.tagName === 'BUTTON'
      if (item === '[role="button"]') return this.role === 'button'
      if (item === '[data-pointer-activation]') return this.pointerActivation
      return false
    })
  }

  closest(selector) {
    let current = this
    while (current) {
      if (current.matches(selector)) return current
      current = current.parentElement
    }
    return null
  }

  contains(element) {
    let current = element
    while (current) {
      if (current === this) return true
      current = current.parentElement
    }
    return false
  }
}

function loadHookWithSynchronousEffect() {
  const source = readFileSync(new URL('./useGlobalOverscrollGuard.js', import.meta.url), 'utf8')
  const executableSource = source
    .replace("import { useEffect } from 'react'\n", '')
    .replace('export default function useGlobalOverscrollGuard', 'function useGlobalOverscrollGuard')

  const moduleFactory = new Function(`
    let cleanup
    const useEffect = (callback) => {
      cleanup = callback()
    }

    ${executableSource}

    return {
      useGlobalOverscrollGuard,
      cleanup: () => cleanup?.(),
    }
  `)

  return moduleFactory()
}

function installAppleTouchGlobals({ root, elementFromPoint }) {
  const documentListeners = new Map()
  globalThis.Element = FakeElement
  globalThis.window = {
    navigator: {
      userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)',
      platform: 'iPad',
      maxTouchPoints: 5,
    },
    getComputedStyle: (element) => element.style,
  }
  globalThis.document = {
    getElementById: (id) => (id === 'root' ? root : null),
    elementFromPoint,
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
  return documentListeners
}

function cleanupGlobals() {
  delete globalThis.document
  delete globalThis.window
  delete globalThis.Element
}

test('iPad overscroll guard does not prevent tiny button tap drift', () => {
  const root = new FakeElement({ style: { overflowY: 'auto' } })
  const button = new FakeElement({ tagName: 'BUTTON', parentElement: root })
  const documentListeners = installAppleTouchGlobals({
    root,
    elementFromPoint: () => button,
  })
  const { useGlobalOverscrollGuard, cleanup } = loadHookWithSynchronousEffect()

  useGlobalOverscrollGuard()

  const touchmove = documentListeners.get('touchmove')
  assert.equal(typeof touchmove?.listener, 'function')

  let prevented = false
  documentListeners.get('touchstart').listener({
    touches: [{ clientX: 40, clientY: 100 }],
    target: button,
  })
  touchmove.listener({
    touches: [{ clientX: 41, clientY: 101 }],
    cancelable: true,
    target: button,
    preventDefault: () => { prevented = true },
  })

  assert.equal(prevented, false)

  cleanup()
  cleanupGlobals()
})

test('iPad overscroll guard still prevents root rubber-band scroll', () => {
  const root = new FakeElement({ style: { overflowY: 'auto' } })
  const label = new FakeElement({ parentElement: root })
  const documentListeners = installAppleTouchGlobals({
    root,
    elementFromPoint: () => label,
  })
  const { useGlobalOverscrollGuard, cleanup } = loadHookWithSynchronousEffect()

  useGlobalOverscrollGuard()

  let prevented = false
  documentListeners.get('touchstart').listener({
    touches: [{ clientX: 40, clientY: 100 }],
    target: label,
  })
  documentListeners.get('touchmove').listener({
    touches: [{ clientX: 40, clientY: 101 }],
    cancelable: true,
    target: label,
    preventDefault: () => { prevented = true },
  })

  assert.equal(prevented, true)

  cleanup()
  cleanupGlobals()
})
