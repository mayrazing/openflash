import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DialogButton, List, ListItem } from 'konsta/react'
import KonstaDialogShell from '../../components/konsta/KonstaDialogShell'
import { withGenericClick } from '../../lib/soundEngine'
import { createCandidateSession } from './candidateSession.js'
import SpeakerIcon from './SpeakerIcon.jsx'
import { ttsApi } from './api.js'

const EMPTY_SESSION_STATE = {
  loadingEngine: null,
  selected: null,
  confirming: false,
  error: null,
}

export default function TtsCandidateDialog({ open, text, onClose }) {
  const { t } = useTranslation()
  const [engines, setEngines] = useState([])
  const [loadingEngines, setLoadingEngines] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [sessionState, setSessionState] = useState(EMPTY_SESSION_STATE)
  const sessionRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    let active = true
    setEngines([])
    setLoadingEngines(true)
    setLoadError(false)
    setSessionState(EMPTY_SESSION_STATE)

    const session = createCandidateSession({
      text,
      previewText: ttsApi.previewText,
      replaceCachedAudio: ttsApi.replaceCachedAudio,
      onStateChange: state => {
        if (active) setSessionState({ ...state })
      },
    })
    sessionRef.current = session

    ttsApi.getTtsEngineOptions()
      .then(options => {
        if (active) setEngines(options)
      })
      .catch(() => {
        if (active) setLoadError(true)
      })
      .finally(() => {
        if (active) setLoadingEngines(false)
      })

    return () => {
      active = false
      session.dispose()
      ttsApi.cancelPlayback()
      if (sessionRef.current === session) sessionRef.current = null
    }
  }, [open, text])

  function preview(engine) {
    sessionRef.current?.preview(engine).catch(() => {})
  }

  async function confirm() {
    try {
      const replaced = await sessionRef.current?.confirm()
      if (replaced) onClose()
    } catch {
      // API 已通过全局 Toast 给出错误提示, 弹窗保留以便用户重试。
    }
  }

  const confirmDisabled = !sessionState.selected
    || sessionState.confirming
    || sessionState.loadingEngine !== null

  return (
    <KonstaDialogShell
      open={open}
      onClose={sessionState.confirming ? undefined : onClose}
      title={t('ttsCandidate.title')}
      ariaLabel={t('ttsCandidate.title')}
      className="!w-[min(30rem,calc(100vw-2rem))]"
      buttons={(
        <>
          <DialogButton
            disabled={sessionState.confirming}
            onClick={withGenericClick(onClose)}
          >
            {t('common.cancel')}
          </DialogButton>
          <DialogButton
            strong
            disabled={confirmDisabled}
            onClick={withGenericClick(confirm)}
          >
            {sessionState.confirming ? t('ttsCandidate.replacing') : t('ttsCandidate.confirm')}
          </DialogButton>
        </>
      )}
    >
      <p className="mb-3 text-sm text-app-label-secondary">{t('ttsCandidate.description')}</p>
      {loadingEngines && (
        <p className="py-4 text-center text-sm text-app-label-secondary">{t('ttsCandidate.loading')}</p>
      )}
      {!loadingEngines && loadError && (
        <p className="py-4 text-center text-sm text-app-danger">{t('ttsCandidate.loadError')}</p>
      )}
      {!loadingEngines && !loadError && engines.length === 0 && (
        <p className="py-4 text-center text-sm text-app-label-secondary">{t('ttsCandidate.empty')}</p>
      )}
      {engines.length > 0 && (
        <List nested strong className="!my-0 overflow-hidden rounded-xl">
          {engines.map(engine => {
            const loading = sessionState.loadingEngine === engine
            const selected = sessionState.selected?.engine === engine
            return (
              <ListItem
                key={engine}
                link
                data-pointer-activation=""
                title={t(`deckSettings.ttsEngine.${engine}`, { defaultValue: engine })}
                aria-label={t('ttsCandidate.previewAriaLabel', {
                  model: t(`deckSettings.ttsEngine.${engine}`, { defaultValue: engine }),
                })}
                onClick={withGenericClick(() => preview(engine))}
                after={(
                  <span className="flex min-w-20 items-center justify-end gap-2 text-primary">
                    {loading && <span className="text-xs text-app-label-secondary">{t('ttsCandidate.previewing')}</span>}
                    {!loading && selected && <span className="text-xs font-medium text-app-success">{t('ttsCandidate.selected')}</span>}
                    <SpeakerIcon className="h-5 w-5" />
                  </span>
                )}
              />
            )
          })}
        </List>
      )}
      {sessionState.error && (
        <p className="mt-3 text-sm text-app-danger">{t('ttsCandidate.previewError')}</p>
      )}
    </KonstaDialogShell>
  )
}
