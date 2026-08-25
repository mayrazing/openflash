import {
  buildCardProgressState,
  buildDistributionState,
  cloneDistributionState,
  syncDistributionWithQueue,
} from './practiceSession.js'
import { clonePendingReplay } from './practiceReplay.js'
import {
  TODAY_REPRACTICE_MODE,
  appendNewCardGroups,
  clampHistoryEntries,
  countBaseNewCards,
  createEmptyStats,
  reconcileQueueForNewLimit,
  snapshotPracticeQueueItem,
} from './practiceQueue.js'

export { TODAY_REPRACTICE_MODE }

const DEFAULT_REVIEW_LOAD_PROFILE = 'standard'

// 返回当前本地日期字符串，供测试和 session 补字段时避免依赖浏览器环境。
function getTodayString() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 统一学习强度默认值，旧 session 或异常设置缺值时回退 standard。
export function normalizeReviewLoadProfile(profile) {
  return profile || DEFAULT_REVIEW_LOAD_PROFILE
}

// 根据当前卡包设置同步 saved session，保持旧队列进度并按后端 fresh queue 追加可用新卡。
export async function buildSessionWithCurrentSettings(session, nextSettings, deckId, dependencies = {}) {
  const buildDailyQueue = dependencies.buildDailyQueue
  const today = dependencies.getToday?.() ?? getTodayString()
  const nextLimit = nextSettings.newCardsPerDay
  const nextReviewLoadProfile = normalizeReviewLoadProfile(nextSettings.reviewLoadProfile)
  const savedReviewLoadProfile = normalizeReviewLoadProfile(session.settingsReviewLoadProfile)
  const hasSavedReviewLoadProfile = Object.prototype.hasOwnProperty.call(session, 'settingsReviewLoadProfile')
  const reviewLoadProfileChanged = savedReviewLoadProfile !== nextReviewLoadProfile
  const settingsChanged = session.settingsNewCardsPerDay !== nextLimit
    || reviewLoadProfileChanged
    || !hasSavedReviewLoadProfile

  const baseSession = {
    ...session,
    sessionSchemaVersion: 2,
    sessionDate: session.sessionDate ?? today,
    settingsNewCardsPerDay: nextLimit,
    settingsReviewLoadProfile: nextReviewLoadProfile,
  }

  if (session.practiceFinished) {
    return {
      session: baseSession,
      changed: session.sessionSchemaVersion !== 2 || !session.sessionDate || settingsChanged,
      appendedCardCount: 0,
    }
  }

  let result = reconcileQueueForNewLimit(
    session.queueItems ?? [],
    session.current ?? 0,
    Boolean(session.revealed),
    session.firstRatedIds ?? [],
    nextLimit
  )
  let appendedCardCount = 0

  if (
    session.mode !== TODAY_REPRACTICE_MODE
    && !result.changed
    && session.mode
    && buildDailyQueue
    && (reviewLoadProfileChanged || nextLimit > countBaseNewCards(session.queueItems ?? []))
  ) {
    const queueData = await buildDailyQueue(deckId, nextLimit, session.mode)
    const appendResult = appendNewCardGroups(session.queueItems ?? [], queueData?.items ?? [], nextLimit)
    if (appendResult.changed) {
      result = {
        changed: true,
        queue: appendResult.queue.map(snapshotPracticeQueueItem),
        current: session.current ?? 0,
        revealed: Boolean(session.revealed),
      }
      appendedCardCount = appendResult.appendedCardCount
    }
  }

  const firstRatedIds = session.firstRatedIds ?? []
  const nextStats = {
    ...createEmptyStats(),
    ...(session.stats ?? {}),
    newCount: countBaseNewCards(result.queue),
  }
  const nextSession = {
    ...baseSession,
    queueItems: result.queue,
    current: result.current,
    revealed: result.revealed,
    stats: nextStats,
    history: result.changed ? [] : clampHistoryEntries(session.history ?? []),
    pendingReplay: result.changed ? null : clonePendingReplay(session.pendingReplay),
    cardProgressState: buildCardProgressState(result.queue.filter(item => !item.isRepractice), firstRatedIds),
    distributionState: result.changed
      ? syncDistributionWithQueue(session.distributionState ?? buildDistributionState(session.queueItems ?? []), result.queue)
      : cloneDistributionState(session.distributionState ?? buildDistributionState(result.queue)),
  }

  return {
    session: nextSession,
    changed: result.changed || session.sessionSchemaVersion !== 2 || !session.sessionDate || settingsChanged,
    appendedCardCount,
  }
}
