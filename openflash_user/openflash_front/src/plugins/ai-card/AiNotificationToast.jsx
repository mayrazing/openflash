import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Toast } from 'konsta/react'
import i18n from '../../i18n.js'
import {
  AI_ERROR_EVENT,
  AI_QUEUED_EVENT,
  AI_READY_EVENT,
} from './aiCacheStatus'
import { AI_TOAST_EXIT_MS, AI_TOAST_VISIBLE_MS } from '../../config/toast'
import { withGenericClick } from '../../lib/soundEngine'

export { AI_ERROR_EVENT, AI_QUEUED_EVENT, AI_READY_EVENT }

function clearTimers(timers) {
  Object.values(timers.current).forEach((timer) => {
    if (timer) {
      clearTimeout(timer)
    }
  })
  timers.current = {}
}

/** 监听 AI 缓存事件，展示排队/就绪/错误 Toast。点击就绪 Toast 派发 ai-card:open 事件。 */
export default function AiNotificationToast() {
  const { t } = useTranslation()
  const nextIdRef = useRef(0)
  const queuedTimersRef = useRef({})
  const readyTimersRef = useRef({})
  const errorTimersRef = useRef({})
  const [queuedToast, setQueuedToast] = useState(null)
  const [readyToast, setReadyToast] = useState(null)
  const [errorToast, setErrorToast] = useState(null)

  useEffect(() => {
    function removeQueued(id) {
      queuedTimersRef.current.remove = setTimeout(() => {
        setQueuedToast((toast) => (toast?.id === id ? null : toast))
      }, AI_TOAST_EXIT_MS)
    }

    function dismissQueued(id) {
      setQueuedToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
      removeQueued(id)
    }

    function showQueued() {
      clearTimers(queuedTimersRef)
      const id = nextIdRef.current + 1
      nextIdRef.current = id

      setQueuedToast({ id, phase: 'enter' })
      queuedTimersRef.current.show = setTimeout(() => {
        setQueuedToast((toast) => (toast?.id === id ? { ...toast, phase: 'visible' } : toast))
      }, 20)
      queuedTimersRef.current.hide = setTimeout(() => dismissQueued(id), AI_TOAST_VISIBLE_MS)
    }

    function removeError(id) {
      errorTimersRef.current.remove = setTimeout(() => {
        setErrorToast((toast) => (toast?.id === id ? null : toast))
      }, AI_TOAST_EXIT_MS)
    }

    function removeReady(id) {
      readyTimersRef.current.remove = setTimeout(() => {
        setReadyToast((toast) => (toast?.id === id ? null : toast))
      }, AI_TOAST_EXIT_MS)
    }

    function dismissReady(id) {
      setReadyToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
      removeReady(id)
    }

    function showReady(event) {
      clearTimers(readyTimersRef)
      const detail = event.detail ?? {}
      const id = nextIdRef.current + 1
      nextIdRef.current = id

      setReadyToast({
        id,
        phase: 'enter',
        cardId: detail.cardId,
        deckId: detail.deckId,
        cardTitle: detail.cardTitle ?? i18n.t('aiToast.unnamed'),
        side: detail.side,
      })
      readyTimersRef.current.show = setTimeout(() => {
        setReadyToast((toast) => (toast?.id === id ? { ...toast, phase: 'visible' } : toast))
      }, 20)
      readyTimersRef.current.hide = setTimeout(() => dismissReady(id), AI_TOAST_VISIBLE_MS)
    }

    function dismissError(id) {
      setErrorToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
      removeError(id)
    }

    function showError(event) {
      clearTimers(errorTimersRef)
      const id = nextIdRef.current + 1
      nextIdRef.current = id

      setErrorToast({
        id,
        phase: 'enter',
        message: event.detail?.message || i18n.t('aiToast.errorDefault'),
      })
      errorTimersRef.current.show = setTimeout(() => {
        setErrorToast((toast) => (toast?.id === id ? { ...toast, phase: 'visible' } : toast))
      }, 20)
      errorTimersRef.current.hide = setTimeout(() => dismissError(id), AI_TOAST_VISIBLE_MS)
    }

    window.addEventListener(AI_QUEUED_EVENT, showQueued)
    window.addEventListener(AI_READY_EVENT, showReady)
    window.addEventListener(AI_ERROR_EVENT, showError)

    return () => {
      window.removeEventListener(AI_QUEUED_EVENT, showQueued)
      window.removeEventListener(AI_READY_EVENT, showReady)
      window.removeEventListener(AI_ERROR_EVENT, showError)
      clearTimers(queuedTimersRef)
      clearTimers(readyTimersRef)
      clearTimers(errorTimersRef)
    }
  }, [])

  function closeQueued() {
    if (!queuedToast) {
      return
    }

    clearTimers(queuedTimersRef)
    const id = queuedToast.id
    setQueuedToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
    queuedTimersRef.current.remove = setTimeout(() => {
      setQueuedToast((toast) => (toast?.id === id ? null : toast))
    }, AI_TOAST_EXIT_MS)
  }

  function openReadyToast() {
    if (!readyToast) {
      return
    }

    clearTimers(readyTimersRef)
    const id = readyToast.id
    const title = readyToast.cardTitle || t('aiCache.dialogTitle')

    setReadyToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
    readyTimersRef.current.remove = setTimeout(() => {
      setReadyToast((toast) => (toast?.id === id ? null : toast))
    }, AI_TOAST_EXIT_MS)

    window.dispatchEvent(new CustomEvent('ai-card:open', {
      detail: {
        cardId: readyToast.cardId,
        title,
        side: readyToast.side,
        deckId: readyToast.deckId,
      },
    }))
  }

  function closeError() {
    if (!errorToast) {
      return
    }

    clearTimers(errorTimersRef)
    const id = errorToast.id
    setErrorToast((toast) => (toast?.id === id ? { ...toast, phase: 'exit' } : toast))
    errorTimersRef.current.remove = setTimeout(() => {
      setErrorToast((toast) => (toast?.id === id ? null : toast))
    }, AI_TOAST_EXIT_MS)
  }

  return (
    <>
      {queuedToast && (
        <Toast
          opened={queuedToast.phase === 'visible'}
          position="center"
          className="z-[80]"
          role="status"
          aria-live="polite"
          button={(
            <Button inline small clear rounded aria-label={t('common.close')} onClick={withGenericClick(closeQueued)}>
              ×
            </Button>
          )}
        >
          <span className="min-w-0 flex-1 text-sm">{t('aiToast.generating')}</span>
        </Toast>
      )}

      {readyToast && (
        <Toast
          component="button"
          type="button"
          opened={readyToast.phase === 'visible'}
          position="right"
          className="z-[80] text-left"
          colors={{ bgIos: '!bg-app-success-fill hover:!bg-app-success-hover active:!bg-app-success-pressed', textIos: '!text-app-on-success' }}
          role="status"
          aria-live="polite"
          onClick={withGenericClick(openReadyToast)}
          aria-label={t('aiToast.readyAriaLabel', { name: readyToast.cardTitle })}
          data-card-id={readyToast.cardId ?? undefined}
        >
          <span className="min-w-0 flex-1 truncate text-sm font-medium">{t('aiToast.readyWithTitle', { name: readyToast.cardTitle })}</span>
        </Toast>
      )}

      {errorToast && (
        <Toast
          opened={errorToast.phase === 'visible'}
          position="center"
          className="z-[80]"
          colors={{ bgIos: '!bg-app-danger-fill', textIos: '!text-app-on-danger' }}
          role="status"
          aria-live="polite"
          button={(
            <Button inline small clear rounded aria-label={t('common.close')} onClick={withGenericClick(closeError)}>
              ×
            </Button>
          )}
        >
          <span className="min-w-0 flex-1 text-sm">{errorToast.message}</span>
        </Toast>
      )}
    </>
  )
}
