import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const distributionGridSource = readFileSync(
  new URL('../components/practice/PracticeDistributionGrid.jsx', import.meta.url),
  'utf8'
)
const indexCssSource = readFileSync(new URL('../index.css', import.meta.url), 'utf8')
const summarySource = readFileSync(new URL('./Summary.jsx', import.meta.url), 'utf8')

function cssBlock(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = indexCssSource.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`, 'm'))
  assert.ok(match, `${selector} should exist`)
  return match[1]
}

test('practice distribution grid container does not use backdrop blur', () => {
  assert.doesNotMatch(distributionGridSource, /backdrop-blur/)
})

test('completed distribution cells use separated green fills instead of oversized glowing dots', () => {
  assert.match(distributionGridSource, /completed \? 'border-app-success bg-app-success-fill'/)
  assert.doesNotMatch(distributionGridSource, /DistributionGreenDot/)
  assert.doesNotMatch(distributionGridSource, /shadow-\[/)
  assert.doesNotMatch(distributionGridSource, /ring-2 ring-app-success/)
})

test('active distribution card keeps a static readable style without infinite breathe animation', () => {
  const activeBlock = cssBlock('.practice-distribution-card-active')

  assert.match(activeBlock, /border-color:/)
  assert.match(activeBlock, /box-shadow:/)
  assert.doesNotMatch(activeBlock, /animation:/)
  assert.doesNotMatch(indexCssSource, /practice-distribution-card-breathe/)
})

test('summary fireworks stop after a short celebration and remove the canvas', () => {
  assert.match(summarySource, /FIREWORK_CELEBRATION_DURATION_MS/)
  assert.match(summarySource, /window\.setTimeout/)
  assert.match(summarySource, /setVisible\(false\)/)
  assert.match(summarySource, /window\.clearTimeout/)
})

test('deck summary shows completed today counts separately from pending counts', () => {
  assert.match(summarySource, /todayCompletedNew/)
  assert.match(summarySource, /todayCompletedReview/)
  assert.match(summarySource, /summary\.todayStudiedNew/)
  assert.match(summarySource, /summary\.todayReviewed/)
  assert.match(summarySource, /pendingNew/)
  assert.match(summarySource, /pendingTotal/)
})
