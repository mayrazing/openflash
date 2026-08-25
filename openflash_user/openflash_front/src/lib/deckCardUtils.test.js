import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isDeferredFilter,
  toApiState,
  compareTodayCards,
  filterCardsByKeyword,
  formatStatCount,
  replaceCardInLoadedList,
  parseCardTextRows,
  computeRangeSelection,
} from './deckCardUtils.js'

const tStub = (key) => key

test('isDeferredFilter 只对 today/tomorrow 为真', () => {
  assert.equal(isDeferredFilter('today'), true)
  assert.equal(isDeferredFilter('tomorrow'), true)
  assert.equal(isDeferredFilter('new'), false)
  assert.equal(isDeferredFilter(null), false)
})

test('toApiState: deferred 筛选返回 null，其余原样', () => {
  assert.equal(toApiState('today'), null)
  assert.equal(toApiState('tomorrow'), null)
  assert.equal(toApiState('new'), 'new')
  assert.equal(toApiState(null), null)
})

test('formatStatCount: null 显示占位 ...，其余原样返回', () => {
  assert.equal(formatStatCount(null), '...')
  assert.equal(formatStatCount(undefined), '...')
  assert.equal(formatStatCount(0), 0)
  assert.equal(formatStatCount(1000), 1000)
})

test('compareTodayCards: 已掌握的排到后面', () => {
  const a = { state: 'mastered', fsrs: {} }
  const b = { state: 'learning', fsrs: {} }
  assert.ok(compareTodayCards(a, b, '2026-06-11') > 0)
  assert.ok(compareTodayCards(b, a, '2026-06-11') < 0)
})

test('compareTodayCards: 同状态按 nextReviewDate 升序', () => {
  const a = { state: 'learning', fsrs: { nextReviewDate: '2026-06-10' } }
  const b = { state: 'learning', fsrs: { nextReviewDate: '2026-06-12' } }
  assert.ok(compareTodayCards(a, b, '2026-06-11') < 0)
})

test('filterCardsByKeyword: 全选所有沿用当前搜索过滤', () => {
  const cards = [
    { id: 1, sideA: 'alpha', sideB: 'front' },
    { id: 2, sideA: 'beta', sideB: 'target back' },
    { id: 3, sideA: 'gamma', sideB: 'front' },
  ]

  assert.deepEqual(filterCardsByKeyword(cards, 'TARGET').map(card => card.id), [2])
  assert.deepEqual(filterCardsByKeyword(cards, '').map(card => card.id), [1, 2, 3])
})

test('parseCardTextRows: 正常行成卡片，缺逗号/缺边记 failure', () => {
  const { cards, invalidCount, failures } = parseCardTextRows('A,B\nnocomma\nC,', tStub)
  assert.deepEqual(cards, [{ sideA: 'A', sideB: 'B' }])
  assert.equal(invalidCount, 2)
  assert.equal(failures[0].reason, 'deckDetail.missingComma')
  assert.equal(failures[1].reason, 'deckDetail.bothSidesRequired')
})

test('parseCardTextRows: 字面 \\n 转成真实换行', () => {
  const { cards } = parseCardTextRows('line1\\nline2,back', tStub)
  assert.equal(cards[0].sideA, 'line1\nline2')
})

test('replaceCardInLoadedList 按 id 替换且不改长度', () => {
  const cards = [{ id: 1, sideA: 'a' }, { id: 2, sideA: 'b' }]
  const next = replaceCardInLoadedList(cards, { id: 2, sideA: 'B2' })
  assert.equal(next.length, 2)
  assert.equal(next[1].sideA, 'B2')
  assert.equal(next[0].sideA, 'a')
})

test('computeRangeSelection: 从锚点向下选中连续区间（select 模式）', () => {
  const items = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }]
  const next = computeRangeSelection({
    items,
    getId: it => it.id,
    anchorIndex: 0,
    currentIndex: 2,
    snapshot: new Set([]),
    preToggledAnchor: null,
  })
  assert.deepEqual([...next].sort(), ['1', '2', '3'])
})

test('computeRangeSelection: 已选区间反向滑回恢复（toggle 语义）', () => {
  const items = [{ id: 1 }, { id: 2 }, { id: 3 }]
  const next = computeRangeSelection({
    items,
    getId: it => it.id,
    anchorIndex: 1,
    currentIndex: 1,
    snapshot: new Set(['2']),
    preToggledAnchor: null,
  })
  assert.deepEqual([...next], [])
})

test('computeRangeSelection: preToggledAnchor 不被再次 toggle', () => {
  const items = [{ id: 1 }, { id: 2 }]
  const next = computeRangeSelection({
    items,
    getId: it => it.id,
    anchorIndex: 0,
    currentIndex: 1,
    snapshot: new Set(['1']),
    preToggledAnchor: '1',
  })
  assert.deepEqual([...next].sort(), ['1', '2'])
})
