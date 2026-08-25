import test from 'node:test'
import assert from 'node:assert/strict'
import { createSpeakerPressController } from './speakerPressController.js'

test('long press opens model chooser and suppresses following short click', () => {
  const actions = []
  let timerCallback = null
  const controller = createSpeakerPressController({
    onShortPress: () => actions.push('short'),
    onLongPress: () => actions.push('long'),
    setTimeoutFn: callback => {
      timerCallback = callback
      return 'timer'
    },
    clearTimeoutFn: () => {},
  })

  controller.pointerDown({ x: 10, y: 20 })
  timerCallback()
  controller.pointerEnd()
  controller.click()

  assert.deepEqual(actions, ['long'])
})

test('moving away cancels long press and leaves ordinary click active', () => {
  const actions = []
  let timerCallback = null
  const controller = createSpeakerPressController({
    onShortPress: () => actions.push('short'),
    onLongPress: () => actions.push('long'),
    setTimeoutFn: callback => {
      timerCallback = callback
      return 'timer'
    },
    clearTimeoutFn: () => {},
  })

  controller.pointerDown({ x: 10, y: 20 })
  controller.pointerMove({ x: 30, y: 20 })
  timerCallback()
  controller.pointerEnd()
  controller.click()

  assert.deepEqual(actions, ['short'])
})

test('pointer cancellation prevents a delayed long-press callback', () => {
  const actions = []
  let timerCallback = null
  const controller = createSpeakerPressController({
    onShortPress: () => actions.push('short'),
    onLongPress: () => actions.push('long'),
    setTimeoutFn: callback => {
      timerCallback = callback
      return 'timer'
    },
    clearTimeoutFn: () => {},
  })

  controller.pointerDown({ x: 10, y: 20 })
  controller.pointerCancel()
  timerCallback()

  assert.deepEqual(actions, [])
})
