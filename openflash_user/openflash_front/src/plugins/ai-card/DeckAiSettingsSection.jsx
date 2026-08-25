import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Card, List, ListItem, Toggle } from 'konsta/react'
import PromptEditDialog from '../../components/PromptEditDialog'
import { getErrorMessage } from '../../lib/errorMessages.js'
import usePromptEditDialog from '../../lib/usePromptEditDialog'
import { withGenericClick } from '../../lib/soundEngine'
import PluginSettingsLink from '../PluginSettingsLink.jsx'
import {
  DEFAULT_DECK_AI_SETTINGS,
  getAiFeatureState,
  getDeckAiSettings,
  saveDeckAiSettings,
} from './api.js'

/** 卡包设置页里的 AI 提示词设置区块，由 ai-card 插件拥有。 */
export default function DeckAiSettingsSection({ deckId }) {
  const { t } = useTranslation()
  const [aiExplanationEnabledA, setAiExplanationEnabledA] = useState(DEFAULT_DECK_AI_SETTINGS.aiExplanationEnabledA)
  const [aiExplanationEnabledB, setAiExplanationEnabledB] = useState(DEFAULT_DECK_AI_SETTINGS.aiExplanationEnabledB)
  const [aiExplanationPromptA, setAiExplanationPromptA] = useState(DEFAULT_DECK_AI_SETTINGS.aiExplanationPromptA ?? '')
  const [aiExplanationPromptB, setAiExplanationPromptB] = useState(DEFAULT_DECK_AI_SETTINGS.aiExplanationPromptB ?? '')
  const [aiCompletionEnabled, setAiCompletionEnabled] = useState(DEFAULT_DECK_AI_SETTINGS.aiCompletionEnabled)
  const [aiCompletionPrompt, setAiCompletionPrompt] = useState(DEFAULT_DECK_AI_SETTINGS.aiCompletionPrompt ?? '')
  const [sideCompletionEnabled, setSideCompletionEnabled] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [savedMessage, setSavedMessage] = useState('')
  const [saveError, setSaveError] = useState('')

  const promptA = usePromptEditDialog(aiExplanationPromptA, setAiExplanationPromptA)
  const promptB = usePromptEditDialog(aiExplanationPromptB, setAiExplanationPromptB)
  const promptCompletion = usePromptEditDialog(aiCompletionPrompt, setAiCompletionPrompt)

  const savedSettingsRef = useRef(null)
  const autoSaveTimerRef = useRef(null)
  const saveNoticeTimerRef = useRef(null)
  const saveInFlightRef = useRef(false)
  const pendingSaveRef = useRef(null)
  const currentDeckIdRef = useRef(deckId)
  const requestVersionRef = useRef(0)
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    currentDeckIdRef.current = deckId
    requestVersionRef.current += 1
    const requestVersion = requestVersionRef.current
    pendingSaveRef.current = null
    saveInFlightRef.current = false
    savedSettingsRef.current = null
    clearTimeout(autoSaveTimerRef.current)
    clearTimeout(saveNoticeTimerRef.current)
    setSavedMessage('')
    setSaveError('')
    setLoaded(false)

    Promise.all([getDeckAiSettings(deckId), getAiFeatureState()])
      .then(([settings, featureState]) => {
        if (!mountedRef.current || requestVersion !== requestVersionRef.current) return
        const promptAValue = settings.aiExplanationPromptA ?? ''
        const promptBValue = settings.aiExplanationPromptB ?? ''
        const completionPromptValue = settings.aiCompletionPrompt ?? ''
        const snap = toAiPayload({
          aiExplanationEnabledA: settings.aiExplanationEnabledA ?? DEFAULT_DECK_AI_SETTINGS.aiExplanationEnabledA,
          aiExplanationEnabledB: settings.aiExplanationEnabledB ?? DEFAULT_DECK_AI_SETTINGS.aiExplanationEnabledB,
          aiExplanationPromptA: promptAValue,
          aiExplanationPromptB: promptBValue,
          aiCompletionEnabled: settings.aiCompletionEnabled ?? DEFAULT_DECK_AI_SETTINGS.aiCompletionEnabled,
          aiCompletionPrompt: completionPromptValue,
        })

        setAiExplanationEnabledA(snap.aiExplanationEnabledA)
        setAiExplanationEnabledB(snap.aiExplanationEnabledB)
        setAiExplanationPromptA(promptAValue)
        setAiExplanationPromptB(promptBValue)
        setAiCompletionEnabled(snap.aiCompletionEnabled)
        setAiCompletionPrompt(completionPromptValue)
        setSideCompletionEnabled(featureState.sideCompletionEnabled === true)
        savedSettingsRef.current = snap
        setLoaded(true)
      })
      .catch((error) => {
        if (!mountedRef.current || requestVersion !== requestVersionRef.current) return
        setSaveError(getErrorMessage(error?.code) || t('deckSettings.aiLoadError'))
      })

    return () => {
      mountedRef.current = false
      clearTimeout(autoSaveTimerRef.current)
      clearTimeout(saveNoticeTimerRef.current)
    }
  }, [deckId, t])

  /** 把页面里的 AI 输入转换成后端保存格式。 */
  function toAiPayload({
    aiExplanationEnabledA,
    aiExplanationEnabledB,
    aiExplanationPromptA,
    aiExplanationPromptB,
    aiCompletionEnabled,
    aiCompletionPrompt,
  }) {
    return {
      aiExplanationEnabledA,
      aiExplanationEnabledB,
      aiExplanationPromptA: aiExplanationPromptA.trim() || null,
      aiExplanationPromptB: aiExplanationPromptB.trim() || null,
      aiCompletionEnabled,
      aiCompletionPrompt: aiCompletionPrompt.trim() || null,
    }
  }

  /** 判断两份 AI 设置是否一致，避免重复保存。 */
  function isSameAiSettings(left, right) {
    if (!left || !right) return false
    return left.aiExplanationEnabledA === right.aiExplanationEnabledA
      && left.aiExplanationEnabledB === right.aiExplanationEnabledB
      && left.aiExplanationPromptA === right.aiExplanationPromptA
      && left.aiExplanationPromptB === right.aiExplanationPromptB
      && left.aiCompletionEnabled === right.aiCompletionEnabled
      && left.aiCompletionPrompt === right.aiCompletionPrompt
  }

  /** 展示插件块内保存成功提示，并复用同一个自动隐藏计时器。 */
  function showSavedNotice() {
    if (!mountedRef.current) return
    setSavedMessage(t('deckSettings.aiSaved'))
    clearTimeout(saveNoticeTimerRef.current)
    saveNoticeTimerRef.current = setTimeout(() => setSavedMessage(''), 1500)
  }

  /** 合并连续自动保存请求，防止快速切换开关时旧请求覆盖新值。 */
  async function saveQueued(payload) {
    const requestDeckId = deckId
    const requestVersion = requestVersionRef.current
    pendingSaveRef.current = payload
    if (saveInFlightRef.current) return
    saveInFlightRef.current = true
    let showSaved = false

    try {
      while (pendingSaveRef.current
        && requestDeckId === currentDeckIdRef.current
        && requestVersion === requestVersionRef.current) {
        const next = pendingSaveRef.current
        pendingSaveRef.current = null
        if (mountedRef.current
          && requestDeckId === currentDeckIdRef.current
          && requestVersion === requestVersionRef.current) {
          setSaveError('')
        }

        try {
          await saveDeckAiSettings(requestDeckId, next)
          if (!mountedRef.current
            || requestDeckId !== currentDeckIdRef.current
            || requestVersion !== requestVersionRef.current) {
            break
          }
          savedSettingsRef.current = next
          showSaved = true
        } catch (error) {
          if (!mountedRef.current
            || requestDeckId !== currentDeckIdRef.current
            || requestVersion !== requestVersionRef.current) {
            break
          }
          showSaved = false
          setSavedMessage('')
          setSaveError(getErrorMessage(error?.code) || t('deckSettings.aiSaveError'))
        }
      }

      if (mountedRef.current
        && requestDeckId === currentDeckIdRef.current
        && requestVersion === requestVersionRef.current
        && showSaved) {
        showSavedNotice()
      }
    } finally {
      if (requestDeckId === currentDeckIdRef.current && requestVersion === requestVersionRef.current) {
        saveInFlightRef.current = false
      }
    }
  }

  useEffect(() => {
    if (!loaded) return
    const payload = toAiPayload({
      aiExplanationEnabledA,
      aiExplanationEnabledB,
      aiExplanationPromptA,
      aiExplanationPromptB,
      aiCompletionEnabled,
      aiCompletionPrompt,
    })
    clearTimeout(autoSaveTimerRef.current)
    if (isSameAiSettings(payload, savedSettingsRef.current)) return
    autoSaveTimerRef.current = setTimeout(() => saveQueued(payload), 400)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aiExplanationEnabledA, aiExplanationEnabledB, aiExplanationPromptA, aiExplanationPromptB,
    aiCompletionEnabled, aiCompletionPrompt, loaded])

  /** 渲染提示词编辑入口，按钮文案展示首行预览。 */
  function PromptTrigger({ label, value, disabled, onClick }) {
    const preview = value.trim()
      ? value.split('\n')[0].slice(0, 50) + (value.length > 50 ? '…' : '')
      : t('deckSettings.useDefaultPrompt')
    return (
      <div className="flex flex-col gap-1 min-w-0">
        {label && <span className="text-sm font-medium text-app-label-secondary">{label}</span>}
        <Button
          type="button"
          small
          outline
          rounded
          onClick={withGenericClick(onClick)}
          disabled={disabled}
          className="min-h-10 w-full !justify-start truncate px-3 text-left text-sm disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
        >
          {preview}
        </Button>
      </div>
    )
  }

  const controlsDisabled = !loaded

  return (
    <Card
      raised
      outline
      contentWrap={false}
      className="!mx-4 !my-0"
      header={(
        <div className="flex items-center gap-3">
          <h2 className="font-semibold">{t('deckSettings.aiPrompts')}</h2>
          <div className="ml-auto flex items-center gap-2">
            {savedMessage && <span className="text-sm text-app-success">{savedMessage} ✓</span>}
            <PluginSettingsLink pluginId="ai-card" />
          </div>
        </div>
      )}
    >
      <List nested strong className="!my-0">
        <ListItem
          title={t('deckSettings.aiExplanation')}
          subtitle={t('deckSettings.aiExplanationDesc')}
          innerChildren={(
            <div className="mt-3 space-y-3">
              <div className="flex gap-6">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-app-label-secondary">{t('common.sideA')}</span>
                  <Toggle
                    checked={aiExplanationEnabledA}
                    onChange={withGenericClick((event) => setAiExplanationEnabledA(event.target.checked))}
                    disabled={controlsDisabled}
                  >
                    <span className="sr-only">{t('common.sideA')}</span>
                  </Toggle>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-sm text-app-label-secondary">{t('common.sideB')}</span>
                  <Toggle
                    checked={aiExplanationEnabledB}
                    onChange={withGenericClick((event) => setAiExplanationEnabledB(event.target.checked))}
                    disabled={controlsDisabled}
                  >
                    <span className="sr-only">{t('common.sideB')}</span>
                  </Toggle>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <PromptTrigger label={t('common.sideA')} value={aiExplanationPromptA} disabled={controlsDisabled || !aiExplanationEnabledA} onClick={promptA.open} />
                <PromptTrigger label={t('common.sideB')} value={aiExplanationPromptB} disabled={controlsDisabled || !aiExplanationEnabledB} onClick={promptB.open} />
              </div>
              <p className="text-xs text-app-label-tertiary">{t('deckSettings.promptCacheWarning')}</p>
            </div>
          )}
        />

        {sideCompletionEnabled && (
          <ListItem
            title={t('deckSettings.sideCompletion')}
            subtitle={t('deckSettings.sideCompletionDesc')}
            after={(
              <Toggle
                checked={aiCompletionEnabled}
                onChange={withGenericClick((event) => setAiCompletionEnabled(event.target.checked))}
                disabled={controlsDisabled}
              >
                <span className="sr-only">{t('deckSettings.sideCompletion')}</span>
              </Toggle>
            )}
            innerChildren={(
              <div className="mt-3">
                <PromptTrigger value={aiCompletionPrompt} disabled={controlsDisabled || !aiCompletionEnabled} onClick={promptCompletion.open} />
              </div>
            )}
          />
        )}
      </List>

      {saveError && <p className="px-4 pb-4 pt-3 text-sm text-app-danger">{saveError}</p>}

      <PromptEditDialog
        open={promptA.isOpen}
        title={t('deckSettings.promptSideATitle')}
        placeholder={t('deckSettings.promptSideAPlaceholder')}
        value={promptA.draft}
        onChange={promptA.setDraft}
        onConfirm={promptA.confirm}
        onCancel={promptA.cancel}
      />
      <PromptEditDialog
        open={promptB.isOpen}
        title={t('deckSettings.promptSideBTitle')}
        placeholder={t('deckSettings.promptSideBPlaceholder')}
        value={promptB.draft}
        onChange={promptB.setDraft}
        onConfirm={promptB.confirm}
        onCancel={promptB.cancel}
      />
      <PromptEditDialog
        open={promptCompletion.isOpen}
        title={t('deckSettings.sideCompletion')}
        placeholder={t('deckSettings.promptCompletionPlaceholder')}
        value={promptCompletion.draft}
        onChange={promptCompletion.setDraft}
        onConfirm={promptCompletion.confirm}
        onCancel={promptCompletion.cancel}
      />
    </Card>
  )
}
