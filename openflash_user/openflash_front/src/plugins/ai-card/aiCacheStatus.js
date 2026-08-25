import { getKnownErrorMessage } from '../../lib/errorMessages.js'
import i18n from '../../i18n.js'

export const AI_QUEUED_EVENT = 'ai-queued'
export const AI_READY_EVENT = 'ai-cache-ready'
export const AI_ERROR_EVENT = 'ai-error'

export const AI_CACHE_STATUS_HIT = 'hit'
export const AI_CACHE_STATUS_QUEUED = 'queued'
export const AI_CACHE_STATUS_DISABLED = 'disabled'

export function parseAiCacheReadyNotificationData(data) {
  let payload
  try {
    payload = JSON.parse(data)
  } catch {
    return null
  }

  if (payload == null || typeof payload !== 'object') {
    return null
  }

  const { cardId, deckId, cardTitle = i18n.t('aiCache.cardTitle'), side } = payload
  if (cardId == null || deckId == null) {
    return null
  }

  return { cardId, deckId, cardTitle, side }
}

function dispatchAiEvent(dispatchEvent, eventName, detail) {
  dispatchEvent(new CustomEvent(eventName, detail == null ? undefined : { detail }))
}

function setupMessageForDisabledResult(result) {
  if (result?.errorCode === 40054 && result?.sideCompletionSetupRequired === true) {
    return i18n.t('aiCache.explanationAndCompletionDisabledMessage')
  }
  return getKnownErrorMessage(result?.errorCode)
}

function dispatchSideCompletionSetupMessage(result, dispatchEvent, errorEventName) {
  if (result?.sideCompletionSetupRequired !== true) return
  dispatchAiEvent(dispatchEvent, errorEventName, {
    message: i18n.t('aiCache.completionDisabledMessage'),
  })
}

export async function openAiCacheOrNotify({
  cardId,
  side,
  title = i18n.t('aiCache.dialogTitle'),
  checkAiCacheStatus,
  dispatchEvent = (event) => window.dispatchEvent(event),
  onHit,
  isIgnoredError = () => false,
  isStale = () => false,
  queuedEventName = AI_QUEUED_EVENT,
  errorEventName = AI_ERROR_EVENT,
  errorMessage = i18n.t('aiCache.errorMessage'),
  emptyMessage = i18n.t('aiCache.emptyMessage'),
}) {
  try {
    const result = await checkAiCacheStatus(cardId, side)
    if (isStale()) return 'stale'

    if (result?.status === AI_CACHE_STATUS_HIT) {
      const markdown = result.content ?? ''
      if (!markdown.trim()) {
        dispatchAiEvent(dispatchEvent, errorEventName, { message: emptyMessage })
        return 'error'
      }
      dispatchSideCompletionSetupMessage(result, dispatchEvent, errorEventName)
      onHit?.({ title, markdown })
      return AI_CACHE_STATUS_HIT
    }

    if (result?.status === AI_CACHE_STATUS_QUEUED) {
      if (result?.sideCompletionSetupRequired === true) {
        dispatchSideCompletionSetupMessage(result, dispatchEvent, errorEventName)
      } else {
        dispatchAiEvent(dispatchEvent, queuedEventName)
      }
      return AI_CACHE_STATUS_QUEUED
    }

    if (result?.status === AI_CACHE_STATUS_DISABLED) {
      const message = setupMessageForDisabledResult(result) ?? errorMessage
      dispatchAiEvent(dispatchEvent, errorEventName, { message })
      return 'error'
    }

    dispatchAiEvent(dispatchEvent, errorEventName, { message: errorMessage })
    return 'error'
  } catch (error) {
    if (isStale()) return 'stale'
    if (isIgnoredError(error)) return 'ignored'
    const message = getKnownErrorMessage(error?.code) ?? errorMessage
    dispatchAiEvent(dispatchEvent, errorEventName, { message })
    return 'error'
  }
}
