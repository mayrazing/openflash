import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { installPointerActivationBridge } from './pointerActivationBridge.js'

class FakeDocument {
  constructor() {
    this.listeners = new Map()
  }

  addEventListener(type, listener) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  removeEventListener(type, listener) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter(item => item !== listener))
  }

  dispatch(type, event) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }
}

class FakeElement {
  constructor(doc, { tagName = 'BUTTON', disabled = false, role = null } = {}) {
    this.doc = doc
    this.tagName = tagName
    this.disabled = disabled
    this.role = role
    this.clickCount = 0
  }

  matches(selector) {
    return selector.split(',').some(part => {
      const item = part.trim()
      if (item === 'button') return this.tagName === 'BUTTON'
      if (item === '[role="button"]') return this.role === 'button'
      if (item === '[data-pointer-activation]') return this.hasPointerActivation
      return false
    })
  }

  closest(selector) {
    return this.matches(selector) ? this : null
  }

  click() {
    const event = createClickEvent(this)
    this.doc.dispatch('click', event)
    if (!event.defaultPrevented) {
      this.clickCount += 1
    }
  }
}

function createPointerEvent(target, { pointerType = 'pen', pointerId = 1 } = {}) {
  return {
    target,
    pointerType,
    pointerId,
    timeStamp: 1000,
    defaultPrevented: false,
    stopped: false,
    preventDefault() {
      this.defaultPrevented = true
    },
    stopImmediatePropagation() {
      this.stopped = true
    },
  }
}

function createClickEvent(target) {
  return {
    target,
    defaultPrevented: false,
    stopped: false,
    preventDefault() {
      this.defaultPrevented = true
    },
    stopImmediatePropagation() {
      this.stopped = true
    },
  }
}

test('pen pointerup dispatches one synthetic click and suppresses the following native click', () => {
  const doc = new FakeDocument()
  const button = new FakeElement(doc)
  installPointerActivationBridge({ doc, now: () => 1000 })

  doc.dispatch('pointerdown', createPointerEvent(button))
  const pointerUp = createPointerEvent(button)
  doc.dispatch('pointerup', pointerUp)

  assert.equal(pointerUp.defaultPrevented, true)
  assert.equal(button.clickCount, 1)

  const nativeClick = createClickEvent(button)
  doc.dispatch('click', nativeClick)
  if (!nativeClick.defaultPrevented) button.clickCount += 1

  assert.equal(nativeClick.defaultPrevented, true)
  assert.equal(nativeClick.stopped, true)
  assert.equal(button.clickCount, 1)
})

test('mouse pointer does not synthesize or suppress native click', () => {
  const doc = new FakeDocument()
  const button = new FakeElement(doc)
  installPointerActivationBridge({ doc, now: () => 1000 })

  doc.dispatch('pointerdown', createPointerEvent(button, { pointerType: 'mouse' }))
  doc.dispatch('pointerup', createPointerEvent(button, { pointerType: 'mouse' }))
  assert.equal(button.clickCount, 0)

  const nativeClick = createClickEvent(button)
  doc.dispatch('click', nativeClick)
  if (!nativeClick.defaultPrevented) button.clickCount += 1

  assert.equal(nativeClick.defaultPrevented, false)
  assert.equal(button.clickCount, 1)
})

test('disabled buttons are not activated from pen pointer events', () => {
  const doc = new FakeDocument()
  const button = new FakeElement(doc, { disabled: true })
  installPointerActivationBridge({ doc, now: () => 1000 })

  doc.dispatch('pointerdown', createPointerEvent(button))
  const pointerUp = createPointerEvent(button)
  doc.dispatch('pointerup', pointerUp)

  assert.equal(pointerUp.defaultPrevented, false)
  assert.equal(button.clickCount, 0)
})

test('long pen presses are left to long-press handlers', () => {
  const doc = new FakeDocument()
  const button = new FakeElement(doc)
  installPointerActivationBridge({ doc, now: () => 1000 })

  doc.dispatch('pointerdown', { ...createPointerEvent(button), timeStamp: 1000 })
  const pointerUp = { ...createPointerEvent(button), timeStamp: 1700 }
  doc.dispatch('pointerup', pointerUp)

  assert.equal(pointerUp.defaultPrevented, false)
  assert.equal(button.clickCount, 0)
})

test('app startup installs the pointer activation bridge once', () => {
  const source = readFileSync(new URL('../main.jsx', import.meta.url), 'utf8')

  assert.match(source, /import \{ installPointerActivationBridge \} from '\.\/lib\/pointerActivationBridge'/)
  assert.match(source, /installPointerActivationBridge\(\)/)
})
