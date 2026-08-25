import test from 'node:test'
import assert from 'node:assert/strict'
import { scrollElementIntoRootCenter } from './rootScroll.js'

test('scrollElementIntoRootCenter scrolls only the root container', () => {
  const scrollCalls = []
  const root = {
    scrollTop: 300,
    clientHeight: 600,
    getBoundingClientRect: () => ({ top: 20 }),
    scrollTo: (options) => scrollCalls.push(options),
  }
  const target = {
    offsetHeight: 80,
    getBoundingClientRect: () => ({ top: 820 }),
    scrollIntoView: () => {
      throw new Error('scrollIntoView should not be used')
    },
  }

  const didScroll = scrollElementIntoRootCenter(root, target, { behavior: 'auto' })

  assert.equal(didScroll, true)
  assert.deepEqual(scrollCalls, [{ top: 840, behavior: 'auto' }])
})
