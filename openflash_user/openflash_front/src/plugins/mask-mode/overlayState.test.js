/**
 * overlayState 纯函数真值表测试。
 * 覆盖 5 个守卫维度（revealed / eligibilityLoaded / eligible / shouldMask / pressed）
 * 的行为输出。
 */

import test from 'node:test'
import assert from 'node:assert/strict'
import { computeOverlayState } from './overlayState.js'

/** 全合格基准：所有守卫通过，未按下 → masked。 */
const baseEligible = {
  revealed: false,
  eligibilityLoaded: true,
  eligible: true,
  shouldMask: true,
  pressed: false,
}

test('revealed=true 一律 hidden（即便资格、决策、pressed 任意组合）', () => {
  for (const eligibilityLoaded of [true, false]) {
    for (const eligible of [true, false]) {
      for (const shouldMask of [true, false]) {
        for (const pressed of [true, false]) {
          const state = computeOverlayState({
            revealed: true,
            eligibilityLoaded,
            eligible,
            shouldMask,
            pressed,
          })
          assert.equal(state, 'hidden', `revealed=true 必须 hidden，实际 ${state}`)
        }
      }
    }
  }
})

test('eligibilityLoaded=false → hidden（避免初始闪烁）', () => {
  const state = computeOverlayState({ ...baseEligible, eligibilityLoaded: false })
  assert.equal(state, 'hidden')
})

test('eligible=false → hidden（资格未通过不渲染）', () => {
  const state = computeOverlayState({ ...baseEligible, eligible: false })
  assert.equal(state, 'hidden')
})

test('shouldMask=false → hidden（稳定决策判定不遮蔽）', () => {
  const state = computeOverlayState({ ...baseEligible, shouldMask: false })
  assert.equal(state, 'hidden')
})

test('全合格 + pressed=false → masked（默认遮蔽态）', () => {
  const state = computeOverlayState({ ...baseEligible, pressed: false })
  assert.equal(state, 'masked')
})

test('全合格 + pressed=true → transparent（按下临时透明）', () => {
  const state = computeOverlayState({ ...baseEligible, pressed: true })
  assert.equal(state, 'transparent')
})

test('eligible=false 优先级高于 pressed：pressed=true 仍 hidden', () => {
  const state = computeOverlayState({ ...baseEligible, eligible: false, pressed: true })
  assert.equal(state, 'hidden')
})

test('shouldMask=false 优先级高于 pressed：pressed=true 仍 hidden', () => {
  const state = computeOverlayState({ ...baseEligible, shouldMask: false, pressed: true })
  assert.equal(state, 'hidden')
})
