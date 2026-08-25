import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getDistributionCardActiveClass,
} from './practiceDistribution.js'

test('getDistributionCardActiveClass 当前卡返回呼吸亮class', () => {
  assert.equal(
    getDistributionCardActiveClass(7, '7'),
    'practice-distribution-card-active'
  )
})

test('getDistributionCardActiveClass 非当前卡不返回class', () => {
  assert.equal(getDistributionCardActiveClass(8, '7'), '')
})
