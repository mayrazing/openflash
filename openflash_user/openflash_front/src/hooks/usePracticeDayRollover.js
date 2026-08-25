import { useEffect } from 'react'
import { clearPracticeSession, getToday } from '../db/database.js'
import { appError } from '../lib/appLog.js'
import { stopBgMusic } from '../lib/soundEngine.js'
import {
  claimPracticeDayRollover,
  millisecondsUntilNextLocalDay,
} from '../lib/practiceDayRollover.js'

export function usePracticeDayRollover(ctx) {
  const {
    id, navigate, t, sessionReady, mode,
    sessionDateRef, practiceDayRolloverRef,
    sessionMutationChainRef, sessionSaveChainRef,
    invalidateQueuedPracticeSessionSaves,
  } = ctx

  useEffect(() => {
    if (!sessionReady || !mode) return undefined

    let timerId

    function exitExpiredPractice() {
      if (!claimPracticeDayRollover(practiceDayRolloverRef, sessionDateRef.current, getToday())) return

      invalidateQueuedPracticeSessionSaves()
      stopBgMusic()

      const rollover = sessionMutationChainRef.current.catch(() => {}).then(async () => {
        await sessionSaveChainRef.current.catch(() => {})
        try {
          await clearPracticeSession(id)
        } catch (error) {
          appError(error?.code ?? 50000, t('practice.dayRolloverClearError'), error)
        } finally {
          navigate(`/deck/${id}`, {
            replace: true,
            state: { practiceDayRolledOver: true },
          })
        }
      })
      sessionMutationChainRef.current = rollover.catch(() => {})
    }

    function scheduleNextCheck() {
      window.clearTimeout(timerId)
      timerId = window.setTimeout(() => {
        exitExpiredPractice()
        if (!practiceDayRolloverRef.current) scheduleNextCheck()
      }, millisecondsUntilNextLocalDay() + 50)
    }

    function handleVisibilityChange() {
      if (document.visibilityState === 'visible') exitExpiredPractice()
    }

    exitExpiredPractice()
    scheduleNextCheck()
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      window.clearTimeout(timerId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [id, mode, sessionReady]) // eslint-disable-line react-hooks/exhaustive-deps
}
