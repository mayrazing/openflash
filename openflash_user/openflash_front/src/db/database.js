import { pruneSavedSessionForRemovedCards } from '../lib/practiceSession.js'
import { invalidateDeckInstalledPlugins } from '../plugins/deckInstalledPluginCache.js'
import {
  publishSessionInvalidation,
  SESSION_INVALIDATION_REASON_STORAGE_KEY,
} from '../auth/sessionInvalidation.js'

const VITE_ENV = import.meta.env ?? { DEV: true }
const API_BASE_URL = VITE_ENV.DEV ? '' : (VITE_ENV.VITE_API_BASE_URL ?? '').trim()

export class UnauthorizedError extends Error {
  /**
   * 表示当前请求缺少有效登录态。
   */
  constructor(message = '请先登录') {
    super(message)
    this.name = 'UnauthorizedError'
  }
}

// ─── 工具函数 ─────────────────────────────────────────────────

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function getToday() {
  return today()
}

function localDateFromTimestamp(timestamp) {
  const d = new Date(timestamp)
  if (Number.isNaN(d.getTime())) return null
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function isPracticeSessionFromToday(session) {
  if (typeof session?.sessionDate === 'string') return session.sessionDate === today()
  return localDateFromTimestamp(session?.savedAt) === today()
}

// 判断卡片是否由后端明确标记为今天仍需复习，不在前端复刻风险排序规则。
export function shouldCardRepeatToday(card, day = today()) {
  if (!card || card.state === 'new' || card.state === 'mastered' || card.state === 'graduated') return false
  return card.todayCalculated === true
    && card.fsrs?.nextReviewDate === day
    && card.fsrs?.lastReviewDate === day
}

function addDays(day, days) {
  const date = new Date(`${day}T00:00:00`)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/**
 * 卡片是否属于“明天复习”集合（与列表标签语义保持一致）。
 */
export function shouldCardRepeatTomorrow(card, day = today()) {
  if (!card || card.state === 'new' || card.state === 'mastered' || card.state === 'graduated') return false

  const next = card.fsrs?.nextReviewDate
  const tomorrow = addDays(day, 1)
  if (next === tomorrow) return true

  // nextReviewDate 仍为今天，但今天已经完成过一次学习，标签会显示“明天复习”。
  return next === day && card.fsrs?.lastReviewDate === day && !shouldCardRepeatToday(card, day)
}

// 开发环境固定走 Vite 代理，生产环境再按环境变量决定后端地址。
export function buildApiUrl(path) {
  return `${API_BASE_URL}${path}`
}

// 尝试解析 JSON，失败时返回 null，避免把纯文本错误当 JSON 处理。
function parseJsonSafely(text) {
  if (!text) {
    return null
  }

  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export async function request(path, options = {}) {
  const body = options.body
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const response = await fetch(buildApiUrl(path), {
    ...options,
    credentials: 'include',
    headers: isFormData
      ? { ...(options.headers ?? {}) }
      : {
          'Content-Type': 'application/json',
          ...(options.headers ?? {}),
        },
  })

  const text = await response.text()
  const payload = parseJsonSafely(text)
  if (response.status === 401) {
    publishSessionInvalidation(payload?.code)
    const err = new UnauthorizedError('请先登录')
    err.code = payload?.code
    throw err
  }
  if (!response.ok || payload?.code !== 200) {
    const err = new Error(`API error ${payload?.code}`)
    err.code = payload?.code
    throw err
  }
  return payload?.data ?? null
}

function normalizeSettingsPayload(partial) {
  const next = { ...partial }
  if (typeof next.lastExportedAt === 'string') {
    const parsed = new Date(next.lastExportedAt)
    if (!Number.isNaN(parsed.getTime())) {
      next.lastExportedAt = parsed.toISOString().slice(0, 19)
    }
  }
  return next
}

// ─── 设置（Settings）─────────────────────────────────────────

const DEFAULT_SETTINGS = {
  key: 'main',
  theme: 'light',
  soundEnabled: true,
  lastExportedAt: null,
  language: 'en',
}

export const DEFAULT_DECK_SETTINGS = {
  newCardsPerDay: 10,
  targetRetention: 0.9,
  reviewLoadProfile: 'standard',
  duplicateSideAEnabled: true,
  duplicateSideBEnabled: false,
}

const TODAY_REPRACTICE_MODE = 'todayRepractice'

export async function getSettings() {
  const settings = await request('/api/settings')
  return settings ? { ...DEFAULT_SETTINGS, ...settings } : { ...DEFAULT_SETTINGS }
}

export async function saveSettings(partial) {
  const settings = await request('/api/settings', {
    method: 'PUT',
    body: JSON.stringify(normalizeSettingsPayload(partial)),
  })
  return settings ? { ...DEFAULT_SETTINGS, ...settings } : { ...DEFAULT_SETTINGS, ...partial }
}

export async function getLanguageOptions() {
  const options = await request('/api/settings/languages')
  return Array.isArray(options) ? options : []
}

export async function getReviewLoadProfiles() {
  return request('/api/deck-settings/review-load-profiles')
}

export async function getDeckSettings(deckId) {
  const settings = await request(`/api/decks/${deckId}/settings`)
  return settings ? { ...DEFAULT_DECK_SETTINGS, ...settings } : { ...DEFAULT_DECK_SETTINGS }
}

export async function saveDeckSettings(deckId, partial) {
  const settings = await request(`/api/decks/${deckId}/settings`, {
    method: 'PUT',
    body: JSON.stringify(partial),
  })
  return settings ? { ...DEFAULT_DECK_SETTINGS, ...settings } : { ...DEFAULT_DECK_SETTINGS, ...partial }
}

// ─── 认证（Auth）─────────────────────────────────────────────

// 查询当前会话对应的登录用户。
export async function getCurrentUser() {
  return request('/api/auth/me')
}

// 使用用户名密码登录。
export async function login(username, password) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

// 注册新用户，并自动建立登录态。
export async function register(username, password, nickname) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, nickname }),
  })
}

// 校验当前密码并替换为新密码。
export async function changePassword(currentPassword, newPassword) {
  return request('/api/auth/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

// 退出当前登录会话。
export async function logout() {
  return request('/api/auth/logout', {
    method: 'POST',
  })
}

/** 清空当前账号的浏览器本地数据，只保留本次失效原因供登录页显示。 */
export function clearLocalAccountSession(reasonKey) {
  try {
    if (typeof localStorage !== 'undefined') localStorage.clear()
  } catch {
    // 浏览器禁用本地存储时，内存登录态仍会被 App 清理。
  }

  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.clear()
      if (reasonKey) sessionStorage.setItem(SESSION_INVALIDATION_REASON_STORAGE_KEY, reasonKey)
    }
  } catch {
    // 登录页仍可通过 React state 显示本次原因。
  }
}

/** 读取上次失效后留给登录页的原因 key。 */
export function getStoredSessionInvalidationReason() {
  try {
    return typeof sessionStorage === 'undefined'
      ? null
      : sessionStorage.getItem(SESSION_INVALIDATION_REASON_STORAGE_KEY)
  } catch {
    return null
  }
}

/** 新会话认证成功后删除旧失效原因。 */
export function clearStoredSessionInvalidationReason() {
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem(SESSION_INVALIDATION_REASON_STORAGE_KEY)
    }
  } catch {
    // 浏览器禁用 sessionStorage 时无需继续处理。
  }
}

// ─── 卡包（Deck）─────────────────────────────────────────────

export async function getAllDecks() {
  return request('/api/decks')
}

export async function createDeck(name) {
  return request('/api/decks', {
    method: 'POST',
    body: JSON.stringify({ name: name.trim() }),
  })
}

export async function renameDeck(id, newName) {
  return request(`/api/decks/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name: newName.trim() }),
  })
}

export async function deleteDeck(id) {
  await request(`/api/decks/${id}`, {
    method: 'DELETE',
  })
}

export async function getDeck(id) {
  return request(`/api/decks/${id}`)
}

// ─── 卡片（Card）─────────────────────────────────────────────

export async function getCardsByDeck(deckId) {
  return request(`/api/decks/${deckId}/cards`)
}

// 分页查询卡包卡片，支持 state 过滤（new/learning/mastered）和 sort 排序。
export async function getCardsPageByDeck(deckId, options = {}) {
  const params = new URLSearchParams()
  if (typeof options.offset === 'number') {
    params.set('offset', String(options.offset))
  }
  if (typeof options.limit === 'number') {
    params.set('limit', String(options.limit))
  }
  if (options.keyword?.trim()) {
    params.set('keyword', options.keyword.trim().toLowerCase())
  }
  if (options.state) {
    params.set('state', options.state)
  }
  if (options.sort) {
    params.set('sort', options.sort)
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request(`/api/decks/${deckId}/cards/page${suffix}`)
}

// 查询卡包详情页顶部统计。
export async function getDeckCardStats(deckId, newCardsLimit) {
  const params = new URLSearchParams()
  if (typeof newCardsLimit === 'number') {
    params.set('newCardsLimit', String(newCardsLimit))
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  const stats = await request(`/api/decks/${deckId}/cards/stats${suffix}`)
  return stats
    ? {
        ...stats,
        backlogCount: stats.backlogCount ?? 0,
        newCardsPaused: Boolean(stats.newCardsPaused),
      }
    : stats
}

// 查询卡包学习统计页概览，避免加载全量卡片。
export async function getDeckLearningStats(deckId, newCardsLimit) {
  const params = new URLSearchParams()
  if (typeof newCardsLimit === 'number') {
    params.set('newCardsLimit', String(newCardsLimit))
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  const stats = await request(`/api/decks/${deckId}/learning-stats${suffix}`)
  return stats
    ? {
        ...stats,
        backlogCount: stats.backlogCount ?? 0,
        newCardsPaused: Boolean(stats.newCardsPaused),
      }
    : stats
}

export async function getCard(id) {
  return request(`/api/cards/${id}`)
}

export async function createCard(deckId, sideA, sideB, sideAImage = [], sideBImage = []) {
  return request(`/api/decks/${deckId}/cards`, {
    method: 'POST',
    body: JSON.stringify({
      sideA: sideA.trim(),
      sideB: sideB.trim(),
      sideAImage,
      sideBImage,
    }),
  })
}

export async function createCardsBatch(deckId, cards) {
  return request(`/api/decks/${deckId}/cards/batch`, {
    method: 'POST',
    body: JSON.stringify({ cards }),
  })
}

export async function moveCardsBatch(sourceDeckId, targetDeckId, cardIds) {
  return request(`/api/decks/${sourceDeckId}/cards/move`, {
    method: 'POST',
    body: JSON.stringify({ targetDeckId, cardIds }),
  })
}

export async function updateCard(id, sideA, sideB, sideAImage = [], sideBImage = []) {
  return request(`/api/cards/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      sideA: sideA.trim(),
      sideB: sideB.trim(),
      sideAImage,
      sideBImage,
    }),
  })
}

export async function deleteCard(id) {
  await request(`/api/cards/${id}`, {
    method: 'DELETE',
  })
}

export async function resetCard(id) {
  return request(`/api/cards/${id}/reset`, {
    method: 'PUT',
  })
}

// ─── 反应时间配置 ──────────────────────────────────────────

export async function getResponseTimeConfig() {
  return request('/api/practice/response-time-config')
}

// ─── FSRS 打分 ────────────────────────────────────────────────

export async function rateCardFsrs(cardId, itemKey, direction, userRating) {
  return request(`/api/cards/${cardId}/reviews`, {
    method: 'POST',
    body: JSON.stringify({
      itemKey,
      direction,
      rating: userRating,
    }),
  })
}

export async function restoreCardFsrs(cardId, snapshot) {
  return request(`/api/cards/${cardId}/progress`, {
    method: 'PUT',
    body: JSON.stringify(snapshot),
  })
}

// ─── 每日记忆队列 ─────────────────────────────────────────────

export async function buildDailyQueue(deckId, newCardsLimit, mode) {
  const params = new URLSearchParams()
  if (typeof newCardsLimit === 'number') {
    params.set('newCardsLimit', String(newCardsLimit))
  }
  if (mode) {
    params.set('mode', mode)
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request(`/api/decks/${deckId}/practice/queue${suffix}`)
}

// 查询后端权威练习摘要，用于取得积压压力和新卡暂停状态。
export async function getPendingPracticeSummary(deckId, newCardsLimit) {
  const params = new URLSearchParams()
  if (typeof newCardsLimit === 'number') {
    params.set('newCardsLimit', String(newCardsLimit))
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  const summary = await request(`/api/decks/${deckId}/practice/summary${suffix}`)
  return summary
    ? {
        ...summary,
        pendingBacklog: summary.pendingBacklog ?? 0,
        newCardsPaused: Boolean(summary.newCardsPaused),
      }
    : null
}

// 查询当前可用的记忆模式，供模式选择页渲染按钮。
export async function getPracticeModes() {
  return request('/api/practice/modes')
}

// ─── ZIP 导出辅助 ─────────────────────────────────────────────

export async function getCardsExportPayload() {
  const allDecks = await getAllDecks()
  const [cardsByDeck, settingsByDeck] = await Promise.all([
    Promise.all(allDecks.map((deck) => getCardsByDeck(deck.id))),
    Promise.all(allDecks.map((deck) => getDeckSettings(deck.id))),
  ])
  const allCards = cardsByDeck.flat()
  const decks = allDecks.map((d, index) => ({ id: d.id, name: d.name, settings: deckSettingsExportPayload(settingsByDeck[index]) }))
  const cards = allCards.map(c => ({
    id: c.id,
    deckId: c.deckId,
    sideA: c.sideA,
    sideAImage: c.sideAImage ?? [],
    sideB: c.sideB,
    sideBImage: c.sideBImage ?? [],
    createdAt: c.createdAt,
    state: c.state,
    fsrs: c.fsrs,
    directionProgresses: c.directionProgresses ?? null,
    firstLearnedDate: c.firstLearnedDate ?? null,
    masteredAt: c.masteredAt ?? null,
  }))
  return { exportedAt: today(), decks, cards }
}

function deckSettingsExportPayload(settings) {
  return {
    newCardsPerDay: settings.newCardsPerDay,
    targetRetention: settings.targetRetention,
    reviewLoadProfile: settings.reviewLoadProfile,
    duplicateSideAEnabled: settings.duplicateSideAEnabled,
    duplicateSideBEnabled: settings.duplicateSideBEnabled,
  }
}

export async function moveToMastered(cardId) {
  return request(`/api/cards/${cardId}/mastered`, {
    method: 'POST',
  })
}

export async function removeFromMastered(cardId) {
  return request(`/api/cards/${cardId}/mastered`, {
    method: 'DELETE',
  })
}

export async function getMasteredCards() {
  return request('/api/cards/mastered')
}

export async function searchMasteredCards(keyword) {
  const kw = keyword.trim()
  if (!kw) return getMasteredCards()
  return request(`/api/cards/mastered?keyword=${encodeURIComponent(kw)}`)
}

// ─── 记忆断点续练（服务端存储）────────────────────────────────

export async function savePracticeSession(deckId, session) {
  await request(`/api/session-store/${deckId}/session`, { method: 'PUT', body: JSON.stringify(session) })
}

export async function loadPracticeSession(deckId) {
  const session = await request(`/api/session-store/${deckId}/session`)
  if (!session) return null
  if (!isPracticeSessionFromToday(session)) {
    await clearPracticeSession(deckId)
    return null
  }
  return {
    ...session,
    retryQueueItems: Array.isArray(session.retryQueueItems) ? session.retryQueueItems : [],
    postRoundRetryCards: Array.isArray(session.postRoundRetryCards) ? session.postRoundRetryCards : [],
    history: Array.isArray(session.history) ? session.history : [],
    pendingReplay: session.pendingReplay ?? null,
  }
}

export async function clearPracticeSession(deckId) {
  await request(`/api/session-store/${deckId}/session`, { method: 'DELETE' })
}

// 重置或删除单张卡后，同步剪掉这张卡遗留在继续记录里的旧现场。
export async function prunePracticeSessionForRemovedCard(deckId, cardId) {
  return prunePracticeSessionForRemovedCards(deckId, [cardId])
}

// 批量删除卡片后，只读写一次继续记录。
export async function prunePracticeSessionForRemovedCards(deckId, cardIds) {
  const session = await loadPracticeSession(deckId)
  if (!session) return null

  const prunedSession = pruneSavedSessionForRemovedCards(session, cardIds)
  if (!prunedSession) {
    await clearPracticeSession(deckId)
    return null
  }

  await savePracticeSession(deckId, prunedSession)
  return prunedSession
}

function isTodayRepracticeSession(session) {
  if (session?.mode === TODAY_REPRACTICE_MODE) return true
  const queueItems = [
    ...(session?.queueItems ?? []),
    ...(session?.retryQueueItems ?? []),
  ]
  return queueItems.length > 0
    && queueItems.every(item => item?.isRepractice && item?.kind === TODAY_REPRACTICE_MODE)
}

export function hasCountableSavedPracticeSession(session) {
  if (Array.isArray(session?.postRoundRetryCards) && session.postRoundRetryCards.length > 0) {
    return true
  }

  const shouldCountSavedSession = session?.mode && !isTodayRepracticeSession(session)
  if (!shouldCountSavedSession) {
    return false
  }

  if (Array.isArray(session.retryQueueItems) && session.retryQueueItems.length > 0) {
    return true
  }

  return !session.practiceFinished
    && Array.isArray(session.queueItems)
    && session.queueItems.length > 0
}

export async function getPendingPracticeItems(deckId, newCardsLimit) {
  const savedSession = await loadPracticeSession(deckId)
  return pendingPracticeItemsFromSession(deckId, newCardsLimit, savedSession)
}

async function pendingPracticeItemsFromSession(deckId, newCardsLimit, savedSession) {
  const merged = new Map()
  const remember = (item) => {
    if (!item?.cardId) return
    const itemKey = item.itemKey ?? `${item.cardId}:${item.direction ?? 'a2b'}:${item.kind ?? 'base'}:${item.ordinal ?? 0}`
    merged.set(itemKey, { ...item, itemKey, cardId: item.cardId })
  }

  const shouldCountSavedSession = savedSession?.mode && !isTodayRepracticeSession(savedSession)
  if (shouldCountSavedSession && Array.isArray(savedSession.queueItems) && savedSession.queueItems.length > 0) {
    const startIndex = Math.min(Math.max(savedSession.current ?? 0, 0), savedSession.queueItems.length - 1)
    const remainingItems = savedSession.practiceFinished ? [] : savedSession.queueItems.slice(startIndex)
    remainingItems
      .filter(item => item?.cardId || item?.id)
      .forEach(item => {
        const cardId = item.cardId ?? item.id
        remember({ cardId, isNew: !!item.isNew, itemKey: item.itemKey, direction: item.direction, kind: item.kind, ordinal: item.ordinal })
      })
  }

  const retryItems = shouldCountSavedSession ? (savedSession?.retryQueueItems ?? []) : []
  const t = today()
  const restoredRetryItems = (await Promise.all(
    retryItems.map(async item => {
      const cardId = item.cardId ?? item.id
      const fullCard = await getCard(cardId)
      if (!fullCard) return null
      return {
        itemKey: item.itemKey ?? `${cardId}:${item.direction ?? 'a2b'}:${item.kind ?? 'retry'}:${item.ordinal ?? 0}`,
        cardId: fullCard.id,
        isNew: fullCard.state === 'new' || fullCard.firstLearnedDate === t,
      }
    })
  )).filter(Boolean)
  restoredRetryItems.forEach(remember)

  const postRoundRetryItems = (await Promise.all(
    (savedSession?.postRoundRetryCards ?? []).map(async item => {
      const cardId = item.cardId ?? item.id
      const fullCard = await getCard(cardId)
      if (!fullCard) return null
      return {
        itemKey: item.itemKey ?? `${cardId}:${item.direction ?? 'a2b'}:${item.kind ?? 'postRoundRetry'}:${item.ordinal ?? 0}`,
        cardId: fullCard.id,
        isNew: fullCard.state === 'new' || fullCard.firstLearnedDate === t,
      }
    })
  )).filter(Boolean)
  postRoundRetryItems.forEach(remember)

  if (merged.size > 0) {
    return [...merged.values()]
  }

  const queue = await buildDailyQueue(deckId, newCardsLimit, 'random')
  ;(queue?.items ?? []).forEach(item => {
    remember({
      itemKey: item.itemKey,
      cardId: item.cardId,
      isNew: !!item.isNew,
      direction: item.direction,
      kind: item.kind,
      ordinal: item.ordinal,
    })
  })
  return [...merged.values()]
}

function pendingPracticeSummaryFromItems(cards, pendingItems, backendSummary) {
  const cardMap = new Map(cards.map(card => [card.id, card]))
  const uniquePendingCards = new Map()
  for (const item of pendingItems) {
    const card = cardMap.get(item.cardId)
    if (!card) continue
    const current = uniquePendingCards.get(card.id)
    uniquePendingCards.set(card.id, {
      card,
      isNew: Boolean(current?.isNew || item.isNew),
    })
  }

  let pendingNew = 0
  let pendingReview = 0
  for (const pendingCard of uniquePendingCards.values()) {
    if (pendingCard.isNew) pendingNew++
    else pendingReview++
  }

  const reviewLimitFields = {}
  if (backendSummary?.targetReviewItemCount !== null && backendSummary?.targetReviewItemCount !== undefined) {
    reviewLimitFields.targetReviewItemCount = backendSummary.targetReviewItemCount
  }
  if (backendSummary?.maxReviewItemCount !== null && backendSummary?.maxReviewItemCount !== undefined) {
    reviewLimitFields.maxReviewItemCount = backendSummary.maxReviewItemCount
  }

  return {
    pendingTotal: uniquePendingCards.size,
    pendingNew,
    pendingReview,
    pendingBacklog: backendSummary?.pendingBacklog ?? 0,
    newCardsPaused: Boolean(backendSummary?.newCardsPaused),
    ...reviewLimitFields,
  }
}

function todayCardsFromItems(allCards, pendingItems) {
  const t = today()
  const cardMap = new Map(allCards.map(card => [card.id, card]))
  const todayCards = new Map()

  for (const item of pendingItems) {
    const card = cardMap.get(item.cardId)
    if (card) todayCards.set(card.id, card)
  }

  for (const card of allCards) {
    if (card.firstLearnedDate === t || card.fsrs?.lastReviewDate === t) {
      todayCards.set(card.id, card)
    }
  }

  return [...todayCards.values()]
}

export async function getDynamicPendingPracticeSummary(deckId, newCardsLimit) {
  const [cards, pendingItems, backendSummary] = await Promise.all([
    getCardsByDeck(deckId),
    getPendingPracticeItems(deckId, newCardsLimit),
    getPendingPracticeSummary(deckId, newCardsLimit),
  ])
  return pendingPracticeSummaryFromItems(cards, pendingItems, backendSummary)
}

export async function getTodayCardsByDeck(deckId, newCardsLimit) {
  const params = new URLSearchParams()
  if (typeof newCardsLimit === 'number') {
    params.set('newCardsLimit', String(newCardsLimit))
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request(`/api/decks/${deckId}/today-cards${suffix}`)
}

export async function getPracticeStartupSnapshot(deckId, newCardsLimit) {
  const savedSession = await loadPracticeSession(deckId)
  const [cards, pendingItems, backendSummary] = await Promise.all([
    getCardsByDeck(deckId),
    pendingPracticeItemsFromSession(deckId, newCardsLimit, savedSession),
    getPendingPracticeSummary(deckId, newCardsLimit),
  ])
  return {
    savedSession,
    pendingSummary: pendingPracticeSummaryFromItems(cards, pendingItems, backendSummary),
    todayCards: todayCardsFromItems(cards, pendingItems),
  }
}

/**
 * 获取卡包内”明天复习”的卡片（不包含新卡和已掌握）。
 */
export async function getTomorrowCardsByDeck(deckId) {
  const allCards = await getCardsByDeck(deckId)
  const t = today()
  return allCards.filter(card => shouldCardRepeatTomorrow(card, t))
}

// ─── 卡包单独导出 ─────────────────────────────────────────────

/**
 * 只导出指定卡包及其卡片内容，格式与全局导出一致
 */
export async function exportDeck(deckId) {
  const deck = await getDeck(deckId)
  if (!deck) throw new Error('卡包不存在')

  const [allCards, settings] = await Promise.all([
    getCardsByDeck(deckId),
    getDeckSettings(deckId),
  ])
  const cards = allCards.map(c => ({
    id: c.id,
    deckId: c.deckId,
    sideA: c.sideA,
    sideAImage: c.sideAImage ?? [],
    sideB: c.sideB,
    sideBImage: c.sideBImage ?? [],
    createdAt: c.createdAt,
  }))

  const payload = { exportedAt: today(), decks: [{ id: deck.id, name: deck.name, settings: deckSettingsExportPayload(settings) }], cards }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  // 卡包名中的特殊字符替换为下划线，保留中文和英文
  const safeName = deck.name.replace(/[^\w\u4e00-\u9fa5]/g, '_')
  a.download = `pickword_${safeName}_${today()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

export async function exportDecks(deckIds) {
  const zipModulePromise = import('jszip')
  const results = await Promise.all(deckIds.map(async (deckId) => {
    const [deck, settings, allCards] = await Promise.all([
      getDeck(deckId),
      getDeckSettings(deckId),
      getCardsByDeck(deckId),
    ])
    if (!deck) return null
    return { deck, settings, allCards }
  }))

  const decks = []
  const cards = []
  for (const r of results) {
    if (!r) continue
    decks.push({ id: r.deck.id, name: r.deck.name, settings: deckSettingsExportPayload(r.settings) })
    for (const c of r.allCards) {
      cards.push({
        id: c.id,
        deckId: c.deckId,
        sideA: c.sideA,
        sideAImage: c.sideAImage ?? [],
        sideB: c.sideB,
        sideBImage: c.sideBImage ?? [],
        createdAt: c.createdAt,
        state: c.state,
        directionProgresses: c.directionProgresses ?? null,
        firstLearnedDate: c.firstLearnedDate ?? null,
        masteredAt: c.masteredAt ?? null,
      })
    }
  }

  const payload = { exportedAt: today(), decks, cards }
  const { default: JSZip } = await zipModulePromise
  const zip = new JSZip()
  zip.file('decks.json', JSON.stringify(payload, null, 2))
  const blob = await zip.generateAsync({ type: 'blob' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `pickword_decks_${today()}.zip`
  a.click()
  URL.revokeObjectURL(url)
}

// ---- 插件市场 ----

// request() 已解包 {code,data} 返回 data，下边 helper 不再二次解包。

/** 拉取插件目录（市场「全部」列表数据源）。 */
export async function getPluginCatalog() {
  const data = await request('/api/plugins/catalog')
  return Array.isArray(data) ? data : []
}

/** 拉取某卡包对当前用户可见的已装插件 id 列表。 */
export async function getInstalledPlugins(deckId) {
  const data = await request(`/api/plugins/installed?deckId=${encodeURIComponent(deckId)}`)
  return Array.isArray(data) ? data : []
}

/** 提交装卸：installDeckIds 安装、uninstallDeckIds 卸载。 */
export async function savePluginInstall(pluginId, installDeckIds, uninstallDeckIds) {
  const result = await request('/api/plugins/install', {
    method: 'POST',
    body: JSON.stringify({ pluginId, installDeckIds, uninstallDeckIds }),
  })
  for (const deckId of [...(installDeckIds ?? []), ...(uninstallDeckIds ?? [])]) {
    invalidateDeckInstalledPlugins(deckId)
  }
  return result
}
