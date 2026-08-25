import test from 'node:test'
import assert from 'node:assert/strict'
import { createSideScrollHintController, getSideHintFromPointer } from './sideScrollHints.js'

// 生成最小指针事件对象，让测试只关注点击位置和命中目标。
function makeEvent({ x, y, target = null, currentTarget = null, timeStamp }) {
  return {
    clientX: x,
    clientY: y,
    target: target ?? currentTarget,
    currentTarget: currentTarget ?? target,
    ...(timeStamp === undefined ? {} : { timeStamp }),
  }
}

test('getSideHintFromPointer returns top or bottom only from the right side hot zones', () => {
  const page = {}
  const win = { innerWidth: 400, innerHeight: 800 }

  assert.equal(getSideHintFromPointer(makeEvent({ x: 300, y: 100, target: page }), win), 'top')
  assert.equal(getSideHintFromPointer(makeEvent({ x: 300, y: 700, target: page }), win), 'bottom')
  assert.equal(getSideHintFromPointer(makeEvent({ x: 100, y: 100, target: page }), win), null)
  assert.equal(getSideHintFromPointer(makeEvent({ x: 300, y: 400, target: page }), win), null)
  assert.equal(getSideHintFromPointer(makeEvent({ x: 300, y: 100, target: {}, currentTarget: page }), win), null)
})

test('createSideScrollHintController calls top or bottom after a short valid press', () => {
  let now = 1000
  const calls = []
  const controller = createSideScrollHintController({
    win: { innerWidth: 400, innerHeight: 800 },
    now: () => now,
    setTimer: () => 1,
    clearTimer: () => {},
    onFeedback: side => calls.push(`feedback:${side}`),
    onTop: () => calls.push('top'),
    onBottom: () => calls.push('bottom'),
    onClick: () => calls.push('click'),
  })
  const page = {}

  controller.pointerDown(makeEvent({ x: 300, y: 100, target: page }))
  now = 1100
  controller.pointerUp(makeEvent({ x: 300, y: 100, target: page }))

  controller.pointerDown(makeEvent({ x: 300, y: 700, target: page }))
  now = 1200
  controller.pointerUp(makeEvent({ x: 300, y: 700, target: page }))

  assert.deepEqual(calls, [
    'feedback:top',
    'click',
    'top',
    'feedback:bottom',
    'click',
    'bottom',
  ])
})

test('createSideScrollHintController uses event timeStamp so main-thread block does not reject a real tap', () => {
  // 模拟 PC 大卡包：按下触发重渲染把主线程阻塞数秒，墙钟 now() 被撑到 6000ms，
  // 但事件自身的 timeStamp 差仅 120ms（真实手势时长）。gate 应认账，照常触发滚动。
  let now = 1000
  const calls = []
  const controller = createSideScrollHintController({
    win: { innerWidth: 400, innerHeight: 800 },
    now: () => now,
    setTimer: () => 1,
    clearTimer: () => {},
    onFeedback: side => calls.push(`feedback:${side}`),
    onTop: () => calls.push('top'),
    onBottom: () => calls.push('bottom'),
    onClick: () => calls.push('click'),
  })
  const page = {}

  controller.pointerDown(makeEvent({ x: 300, y: 100, target: page, timeStamp: 500 }))
  now = 7000 // 主线程阻塞 6000ms 后 pointerUp 才执行
  controller.pointerUp(makeEvent({ x: 300, y: 100, target: page, timeStamp: 620 }))

  assert.deepEqual(calls, ['feedback:top', 'click', 'top'])
})

test('createSideScrollHintController ignores long presses and left side releases', () => {
  let now = 1000
  const calls = []
  const controller = createSideScrollHintController({
    win: { innerWidth: 400, innerHeight: 800 },
    now: () => now,
    setTimer: () => 1,
    clearTimer: () => {},
    onFeedback: side => calls.push(`feedback:${side}`),
    onClearFeedback: () => calls.push('clear'),
    onTop: () => calls.push('top'),
    onBottom: () => calls.push('bottom'),
    onClick: () => calls.push('click'),
  })
  const page = {}

  controller.pointerDown(makeEvent({ x: 300, y: 100, target: page }))
  now = 1501
  controller.pointerUp(makeEvent({ x: 300, y: 100, target: page }))

  controller.pointerDown(makeEvent({ x: 300, y: 700, target: page }))
  now = 1520
  controller.pointerUp(makeEvent({ x: 100, y: 700, target: page }))

  assert.deepEqual(calls, ['feedback:top', 'clear', 'feedback:bottom', 'clear'])
})
