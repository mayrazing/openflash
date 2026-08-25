import { useEffect, useRef, useState } from 'react'
import { Toast } from 'konsta/react'
import i18n from '../../i18n.js'
import { TTS_ERROR_EVENT } from './api.js'

export default function TtsToast() {
  const [toast, setToast] = useState({ id: 0, message: '', phase: 'idle' })
  const displayTimerRef = useRef(null)
  const fallbackTimerRef = useRef(null)
  const frameRef = useRef(null)
  const toastIdRef = useRef(0)

  useEffect(() => {
    function clearDisplayTimer() {
      if (displayTimerRef.current) {
        clearTimeout(displayTimerRef.current)
        displayTimerRef.current = null
      }
    }

    function clearFallbackTimer() {
      if (fallbackTimerRef.current) {
        clearTimeout(fallbackTimerRef.current)
        fallbackTimerRef.current = null
      }
    }

    function clearFrame() {
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current)
        frameRef.current = null
      }
    }

    function finishExit(toastId) {
      if (toastIdRef.current !== toastId) {
        return
      }

      clearFallbackTimer()
      setToast((currentToast) =>
        currentToast.id === toastId ? { id: toastId, message: '', phase: 'idle' } : currentToast
      )
    }

    function startExit(toastId) {
      if (toastIdRef.current !== toastId) {
        return
      }

      setToast((currentToast) =>
        currentToast.id === toastId ? { ...currentToast, phase: 'exiting' } : currentToast
      )
      clearFallbackTimer()
      fallbackTimerRef.current = setTimeout(() => {
        fallbackTimerRef.current = null
        finishExit(toastId)
      }, 350)
    }

    function handleError(event) {
      const toastId = toastIdRef.current + 1
      toastIdRef.current = toastId
      clearDisplayTimer()
      clearFallbackTimer()
      clearFrame()
      setToast({
        id: toastId,
        message: event.detail?.message || i18n.t('aiToast.ttsError'),
        phase: 'entering',
      })
      frameRef.current = requestAnimationFrame(() => {
        frameRef.current = null
        setToast((currentToast) =>
          currentToast.id === toastId ? { ...currentToast, phase: 'visible' } : currentToast
        )
      })
      displayTimerRef.current = setTimeout(() => {
        displayTimerRef.current = null
        startExit(toastId)
      }, 2600)
    }

    window.addEventListener(TTS_ERROR_EVENT, handleError)
    return () => {
      window.removeEventListener(TTS_ERROR_EVENT, handleError)
      clearDisplayTimer()
      clearFallbackTimer()
      clearFrame()
    }
  }, [])

  function handleTransitionEnd(event) {
    if (
      event.currentTarget !== event.target ||
      !['opacity', 'transform'].includes(event.propertyName)
    ) {
      return
    }

    if (toast.phase !== 'exiting' || toastIdRef.current !== toast.id) {
      return
    }

    if (fallbackTimerRef.current) {
      clearTimeout(fallbackTimerRef.current)
      fallbackTimerRef.current = null
    }

    setToast({ id: toast.id, message: '', phase: 'idle' })
  }

  if (toast.phase === 'idle' || !toast.message) return null

  return (
    <Toast
      opened={toast.phase === 'visible'}
      position="center"
      colors={{ bgIos: '!bg-app-danger-fill', textIos: '!text-app-on-danger' }}
      role="status"
      aria-live="polite"
      onTransitionEnd={handleTransitionEnd}
    >
      <span className="text-center text-sm">{toast.message}</span>
    </Toast>
  )
}
