import test from 'node:test'
import assert from 'node:assert/strict'
import { createStableMaskDecision } from './maskDecision.js'

// 稳定遮蔽决策纯函数测试：
// - 不依赖 React/DOM/网络，random 函数注入。
// - questionSide 取值对齐项目约定：'a' / 'b'（见 maskEligibility.js）。
// - mode 取值：'random' | 'full'，非法值回退 random（与 maskEligibility 一致）。
// - 缓存 key 为 `${itemKey}:${questionSide}`，random 仅决定整面遮蔽 boolean。

test('同 itemKey+questionSide 多次调用返回同一结果', () => {
  let calls = 0
  const decision = createStableMaskDecision({ random: () => { calls++; return 0.0 } })
  const r1 = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  const r2 = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  const r3 = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  assert.equal(r1, r2)
  assert.equal(r2, r3)
  // 稳定性核心：random 只在首次调用一次，后续命中缓存。
  assert.equal(calls, 1)
})

test('不同 itemKey 可产生不同随机结果', () => {
  const seq = [0.0, 1.0]
  let i = 0
  const decision = createStableMaskDecision({ random: () => seq[i++] })
  const r1 = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  const r2 = decision.shouldMask({ itemKey: 'k2', questionSide: 'a', eligible: true, mode: 'random' })
  assert.notEqual(r1, r2)
})

test('不同 questionSide 可产生不同随机结果', () => {
  const seq = [0.0, 1.0]
  let i = 0
  const decision = createStableMaskDecision({ random: () => seq[i++] })
  const r1 = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  const r2 = decision.shouldMask({ itemKey: 'k1', questionSide: 'b', eligible: true, mode: 'random' })
  assert.notEqual(r1, r2)
})

test('eligible=false 时永不遮蔽（random/full 均不遮蔽，且不调 random）', () => {
  let calls = 0
  const decision = createStableMaskDecision({ random: () => { calls++; return 0.0 } })
  assert.equal(decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: false, mode: 'random' }), false)
  assert.equal(decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: false, mode: 'full' }), false)
  assert.equal(calls, 0)
})

test('full 且 eligible=true 时总是遮蔽（不调 random）', () => {
  let calls = 0
  const decision = createStableMaskDecision({ random: () => { calls++; return 1.0 } })
  assert.equal(decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'full' }), true)
  assert.equal(decision.shouldMask({ itemKey: 'k2', questionSide: 'b', eligible: true, mode: 'full' }), true)
  assert.equal(calls, 0)
})

test('random 且 eligible=true 时使用注入 random 决定整面遮蔽', () => {
  // 低随机值 → 遮蔽；高随机值 → 不遮蔽。
  const low = createStableMaskDecision({ random: () => 0.0 })
  assert.equal(low.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' }), true)
  const high = createStableMaskDecision({ random: () => 1.0 })
  assert.equal(high.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' }), false)
})

test('random 决策只返回 boolean，不遮蔽部分内容', () => {
  const decision = createStableMaskDecision({ random: () => 0.0 })
  const r = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  assert.equal(typeof r, 'boolean')
})

test('不同实例缓存相互隔离', () => {
  const d1 = createStableMaskDecision({ random: () => 0.0 })
  const d2 = createStableMaskDecision({ random: () => 1.0 })
  assert.equal(d1.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' }), true)
  assert.equal(d2.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' }), false)
})

test('默认使用 Math.random 时返回 boolean', () => {
  const decision = createStableMaskDecision()
  const r = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' })
  assert.equal(typeof r, 'boolean')
})

test('非法 mode 回退 random 行为（与 maskEligibility 一致）', () => {
  let calls = 0
  const decision = createStableMaskDecision({ random: () => { calls++; return 0.0 } })
  const r = decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'weird' })
  assert.equal(r, true)
  assert.equal(calls, 1)
})

test('eligible=false 不写入缓存：同 key 后续 eligible=true 仍可重新决策', () => {
  const seq = [0.0]
  let i = 0
  const decision = createStableMaskDecision({ random: () => seq[i++] })
  // 先 eligible=false → false，不进缓存。
  assert.equal(decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: false, mode: 'random' }), false)
  // 同 key eligible=true → 触发首次 random 决策。
  assert.equal(decision.shouldMask({ itemKey: 'k1', questionSide: 'a', eligible: true, mode: 'random' }), true)
  assert.equal(i, 1)
})
