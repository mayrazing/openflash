export const mockDeck = {
  id: 1,
  name: 'Responsive Deck',
  totalCount: 3,
  activeCount: 2,
  masteredCount: 1,
  newCount: 1,
  learningCount: 2,
}

export const mockCards = [
  {
    id: 101,
    deckId: 1,
    sideA: 'ability to adapt to very small screens',
    sideAImage: [],
    sideB: '适配很小屏幕的能力',
    sideBImage: [],
    state: 'new',
    firstLearnedDate: null,
    fsrs: {},
  },
  {
    id: 102,
    deckId: 1,
    sideA: 'layout should scroll instead of hiding actions',
    sideAImage: [],
    sideB: '布局应该滚动，而不是隐藏操作按钮',
    sideBImage: [],
    state: 'learning',
    firstLearnedDate: '2026-06-30',
    fsrs: {
      lastReviewDate: null,
      nextReviewDate: '2026-06-30',
    },
  },
]

const mockMasteredCards = [
  {
    id: 201,
    deckId: 1,
    deckName: 'Responsive Deck',
    sideA: 'mastered layout',
    sideAImage: [],
    sideB: '已掌握布局',
    sideBImage: [],
    masteredAt: '2026-06-30',
  },
]

function ok(data) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data }),
  }
}

export async function installMockApi(page) {
  await page.route('**/api/plugins/active', route => route.fulfill(ok([])))
  await page.route('**/api/auth/me', route => route.fulfill(ok({ id: 1, username: 'tester', nickname: 'Tester' })))
  await page.route('**/api/settings/languages', route => route.fulfill(ok([
    { value: 'zh', label: '中文' },
    { value: 'en', label: 'English' },
  ])))
  await page.route('**/api/settings', route => route.fulfill(ok({ theme: 'light', soundEnabled: false, language: 'zh' })))
  await page.route('**/api/deck-settings/review-load-profiles', route => route.fulfill(ok([
    { value: 'standard', label: '标准' },
  ])))
  await page.route('**/api/decks', route => route.fulfill(ok([mockDeck])))
  await page.route('**/api/decks/1/settings', route => route.fulfill(ok({
    newCardsPerDay: 10,
    targetRetention: 0.9,
    reviewLoadProfile: 'standard',
    duplicateSideAEnabled: true,
    duplicateSideBEnabled: false,
  })))
  await page.route('**/api/decks/1/cards/stats**', route => route.fulfill(ok({
    totalCount: 2,
    newCount: 1,
    learningCount: 1,
    masteredCount: 0,
    graduatedCount: 0,
    todayCount: 1,
    tomorrowCount: 0,
    backlogCount: 0,
    newCardsPaused: false,
  })))
  await page.route('**/api/decks/1/cards/page**', route => route.fulfill(ok({
    items: mockCards,
    total: mockCards.length,
    hasMore: false,
  })))
  await page.route('**/api/decks/1/cards/batch', route => route.fulfill(ok({
    createdCount: 1,
    duplicateCount: 0,
    invalidCount: 0,
    failures: [],
  })))
  await page.route('**/api/decks/1/cards', route => route.fulfill(ok(mockCards)))
  await page.route('**/api/decks/1/practice/summary**', route => route.fulfill(ok({
    pendingTotal: 2,
    pendingNew: 1,
    pendingReview: 1,
    pendingBacklog: 0,
    newCardsPaused: false,
  })))
  await page.route('**/api/decks/1/practice/queue**', route => route.fulfill(ok({
    items: [
      { itemKey: '101:a2b:base:0', cardId: 101, direction: 'a2b', kind: 'base', ordinal: 0, isNew: true, card: mockCards[0] },
      { itemKey: '102:a2b:base:0', cardId: 102, direction: 'a2b', kind: 'base', ordinal: 0, isNew: false, card: mockCards[1] },
    ],
    newCardCount: 1,
    reviewCardCount: 1,
  })))
  await page.route('**/api/decks/1/today-cards**', route => route.fulfill(ok(mockCards)))
  await page.route('**/api/decks/1', route => route.fulfill(ok(mockDeck)))
  await page.route('**/api/practice/modes', route => route.fulfill(ok([
    { value: 'smart', label: '智能复习', desc: '按计划复习' },
  ])))
  await page.route('**/api/practice/response-time-config', route => route.fulfill(ok({
    noRecallMillis: 3000,
    slowRecallMillis: 8000,
  })))
  await page.route('**/api/session-store/1/session', route => route.fulfill(ok(null)))
  await page.route('**/api/plugins/catalog', route => route.fulfill(ok([
    {
      pluginId: 'responsive-test-plugin',
      name: 'Responsive Test Plugin',
      config: JSON.stringify({ desc: 'Used by responsive e2e', icon: 'R' }),
    },
  ])))
  await page.route('**/api/plugins/installed**', route => route.fulfill(ok([])))
  await page.route('**/api/plugins/install', route => route.fulfill(ok({})))
  await page.route('**/api/cards/mastered**', route => route.fulfill(ok(mockMasteredCards)))
}
