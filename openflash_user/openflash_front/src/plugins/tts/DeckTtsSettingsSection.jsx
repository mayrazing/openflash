import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, List, ListItem, Segmented, SegmentedButton, Toggle } from 'konsta/react'
import { getErrorMessage } from '../../lib/errorMessages.js'
import { withGenericClick } from '../../lib/soundEngine'
import PluginSettingsLink from '../PluginSettingsLink.jsx'
import { DEFAULT_DECK_TTS_SETTINGS, ttsApi } from './api.js'
import { createDeckTtsSettingsSaveController } from './deckTtsSettingsSaveController.js'

function chooseAvailableEngine(preferred, availableEngines) {
  if (availableEngines.includes(preferred)) return preferred
  return availableEngines[0] ?? DEFAULT_DECK_TTS_SETTINGS.engine
}

function toTtsPayload({ autoSpeakA, autoSpeakB, engine }) {
  return { autoSpeakA, autoSpeakB, engine }
}

function isSameTtsSettings(left, right) {
  if (!left || !right) return false
  return left.autoSpeakA === right.autoSpeakA
    && left.autoSpeakB === right.autoSpeakB
    && left.engine === right.engine
}

/** 卡包级自动朗读和默认主模型设置。 */
export default function DeckTtsSettingsSection({ deckId }) {
  const { t } = useTranslation()
  const [autoSpeakA, setAutoSpeakA] = useState(DEFAULT_DECK_TTS_SETTINGS.autoSpeakA)
  const [autoSpeakB, setAutoSpeakB] = useState(DEFAULT_DECK_TTS_SETTINGS.autoSpeakB)
  const [engine, setEngine] = useState(DEFAULT_DECK_TTS_SETTINGS.engine)
  const [engineOptions, setEngineOptions] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [savedMessage, setSavedMessage] = useState('')
  const [saveError, setSaveError] = useState('')

  const savedSettingsRef = useRef(null)
  const saveNoticeTimerRef = useRef(null)
  const currentDeckIdRef = useRef(deckId)
  const requestVersionRef = useRef(0)
  const mountedRef = useRef(false)
  const saveControllerRef = useRef(null)

  const showSavedNotice = useCallback(() => {
    if (!mountedRef.current) return
    setSavedMessage(t('deckSettings.basicSaved'))
    clearTimeout(saveNoticeTimerRef.current)
    saveNoticeTimerRef.current = setTimeout(() => setSavedMessage(''), 1500)
  }, [t])

  useEffect(() => {
    mountedRef.current = true
    currentDeckIdRef.current = deckId
    requestVersionRef.current += 1
    const requestVersion = requestVersionRef.current
    savedSettingsRef.current = null
    saveControllerRef.current = createDeckTtsSettingsSaveController({
      deckId,
      saveDeckTtsSettings: ttsApi.saveDeckTtsSettings,
      onChanged: ttsApi.dispatchDeckTtsSettingsChanged,
      onBeforeSave: () => setSaveError(''),
      onSaved: settingsPayload => {
        savedSettingsRef.current = settingsPayload
        showSavedNotice()
      },
      onError: error => {
        setSavedMessage('')
        setSaveError(getErrorMessage(error?.code))
      },
      isActive: () => mountedRef.current
        && deckId === currentDeckIdRef.current
        && requestVersion === requestVersionRef.current,
    })
    clearTimeout(saveNoticeTimerRef.current)
    setSavedMessage('')
    setSaveError('')
    setLoaded(false)

    Promise.all([ttsApi.getDeckTtsSettings(deckId), ttsApi.getTtsEngineOptions()])
      .then(([settings, availableEngines]) => {
        if (!mountedRef.current || requestVersion !== requestVersionRef.current) return
        const snapshot = toTtsPayload({
          autoSpeakA: settings.autoSpeakA ?? DEFAULT_DECK_TTS_SETTINGS.autoSpeakA,
          autoSpeakB: settings.autoSpeakB ?? DEFAULT_DECK_TTS_SETTINGS.autoSpeakB,
          engine: chooseAvailableEngine(settings.engine, availableEngines),
        })
        setAutoSpeakA(snapshot.autoSpeakA)
        setAutoSpeakB(snapshot.autoSpeakB)
        setEngine(snapshot.engine)
        setEngineOptions(availableEngines)
        savedSettingsRef.current = snapshot
        setLoaded(true)
      })
      .catch(error => {
        if (!mountedRef.current || requestVersion !== requestVersionRef.current) return
        setSaveError(getErrorMessage(error?.code))
      })

    return () => {
      mountedRef.current = false
      clearTimeout(saveNoticeTimerRef.current)
      saveControllerRef.current?.dispose()
      saveControllerRef.current = null
    }
  }, [deckId, showSavedNotice])

  useEffect(() => {
    if (!loaded) return
    const settingsPayload = toTtsPayload({ autoSpeakA, autoSpeakB, engine })
    if (isSameTtsSettings(settingsPayload, savedSettingsRef.current)) return
    saveControllerRef.current?.schedule(settingsPayload)
  }, [autoSpeakA, autoSpeakB, engine, loaded])

  const controlsDisabled = !loaded

  return (
    <Card
      raised
      outline
      contentWrap={false}
      className="!mx-4 !my-0 overflow-hidden"
      header={(
        <div className="flex items-center gap-3">
          <h2 className="font-semibold">{t('deckSettings.autoSpeak')}</h2>
          <div className="ml-auto flex items-center gap-2">
            {savedMessage && <span className="text-sm text-app-success">{savedMessage} ✓</span>}
            <PluginSettingsLink pluginId="tts" />
          </div>
        </div>
      )}
    >
      <p className="px-4 pb-3 pt-4 text-sm text-app-label-secondary">{t('deckSettings.autoSpeakDesc')}</p>
      <List nested strong className="!my-0">
        <ListItem title={t('deckSettings.ttsEngine.title')} />
        <ListItem
          innerChildren={(
            <Segmented strong rounded className="grid w-full grid-cols-2">
              {engineOptions.map(engineKey => (
                <SegmentedButton
                  key={engineKey}
                  type="button"
                  active={engine === engineKey}
                  disabled={controlsDisabled}
                  onClick={withGenericClick(() => setEngine(engineKey))}
                  className="min-h-10 min-w-0 px-2 text-sm disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
                >
                  {t(`deckSettings.ttsEngine.${engineKey}`, { defaultValue: engineKey })}
                </SegmentedButton>
              ))}
            </Segmented>
          )}
        />
        <ListItem
          title={t('common.sideA')}
          after={(
            <Toggle
              checked={autoSpeakA}
              onChange={withGenericClick(event => setAutoSpeakA(event.target.checked))}
              disabled={controlsDisabled}
            >
              <span className="sr-only">{t('common.sideA')}</span>
            </Toggle>
          )}
        />
        <ListItem
          title={t('common.sideB')}
          after={(
            <Toggle
              checked={autoSpeakB}
              onChange={withGenericClick(event => setAutoSpeakB(event.target.checked))}
              disabled={controlsDisabled}
            >
              <span className="sr-only">{t('common.sideB')}</span>
            </Toggle>
          )}
        />
      </List>
      {saveError && <p className="px-4 pb-4 pt-3 text-sm text-app-danger">{saveError}</p>}
    </Card>
  )
}
