import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, List, ListItem, Segmented, SegmentedButton, Toggle } from 'konsta/react'
import { getErrorMessage } from '../../lib/errorMessages.js'
import { withGenericClick } from '../../lib/soundEngine'
import PluginSettingsLink from '../PluginSettingsLink.jsx'
import {
  DEFAULT_DECK_MASK_MODE_SETTINGS,
  dispatchDeckMaskModeSettingsChanged,
  getDeckMaskModeSettings,
  saveDeckMaskModeSettings,
} from './api.js'
import { createDeckMaskModeSettingsSaveController } from './deckMaskModeSettingsSaveController.js'

/** 卡包设置页里的遮蔽模式区块，由 mask-mode 插件拥有。卡包级总开关 + 模式选择（随机 / 完全）。 */
export default function DeckMaskModeSettingsSection({ deckId }) {
  const { t } = useTranslation()
  const [mode, setMode] = useState(DEFAULT_DECK_MASK_MODE_SETTINGS.mode)
  const [enabled, setEnabled] = useState(DEFAULT_DECK_MASK_MODE_SETTINGS.enabled)
  const [loaded, setLoaded] = useState(false)
  const [savedMessage, setSavedMessage] = useState('')
  const [saveError, setSaveError] = useState('')

  const savedModeRef = useRef(null)
  const savedEnabledRef = useRef(null)
  const saveNoticeTimerRef = useRef(null)
  const currentDeckIdRef = useRef(deckId)
  const mountedRef = useRef(false)
  const saveControllerRef = useRef(null)

  /** 展示插件块内保存成功提示，并复用同一个自动隐藏计时器。 */
  const showSavedNotice = useCallback(function showSavedNotice() {
    if (!mountedRef.current) return
    setSavedMessage(t('deckSettings.basicSaved'))
    clearTimeout(saveNoticeTimerRef.current)
    saveNoticeTimerRef.current = setTimeout(() => setSavedMessage(''), 1500)
  }, [t])

  useEffect(() => {
    mountedRef.current = true
    currentDeckIdRef.current = deckId
    savedModeRef.current = null
    savedEnabledRef.current = null
    // 初始化 effect 时构建一次 controller，存入 ref；deckId 变化会重跑 effect 覆盖
    // 旧 controller，避免每次切换 mode/enabled 都重建（与 TTS 同款 saveControllerRef 风格）。
    saveControllerRef.current = createDeckMaskModeSettingsSaveController({
      deckId,
      saveDeckMaskModeSettings,
      onChanged: dispatchDeckMaskModeSettingsChanged,
      onSuccess: ({ mode: savedMode, enabled: savedEnabled }) => {
        savedModeRef.current = savedMode
        savedEnabledRef.current = savedEnabled
        showSavedNotice()
      },
      onError: (error) => {
        setMode(savedModeRef.current ?? DEFAULT_DECK_MASK_MODE_SETTINGS.mode)
        setEnabled(savedEnabledRef.current ?? DEFAULT_DECK_MASK_MODE_SETTINGS.enabled)
        setSavedMessage('')
        setSaveError(getErrorMessage(error?.code))
      },
      getCurrentDeckId: () => currentDeckIdRef.current,
      isMounted: () => mountedRef.current,
    })
    clearTimeout(saveNoticeTimerRef.current)
    setSavedMessage('')
    setSaveError('')
    setLoaded(false)

    let cancelled = false
    getDeckMaskModeSettings(deckId)
      .then((settings) => {
        if (cancelled || !mountedRef.current || deckId !== currentDeckIdRef.current) return
        const nextMode = settings.mode ?? DEFAULT_DECK_MASK_MODE_SETTINGS.mode
        const nextEnabled = settings.enabled ?? DEFAULT_DECK_MASK_MODE_SETTINGS.enabled
        setMode(nextMode)
        setEnabled(nextEnabled)
        savedModeRef.current = nextMode
        savedEnabledRef.current = nextEnabled
        setLoaded(true)
      })
      .catch((error) => {
        if (cancelled || !mountedRef.current || deckId !== currentDeckIdRef.current) return
        setSaveError(getErrorMessage(error?.code))
      })

    return () => {
      cancelled = true
      mountedRef.current = false
      clearTimeout(saveNoticeTimerRef.current)
    }
  }, [deckId, showSavedNotice])

  /**
   * 统一保存入口：调用 effect 内创建好的同一个 controller 实例，避免每次切换都重建
   * 调度对象，与 TTS Section 的 saveControllerRef 风格保持一致。
   */
  async function persist({ mode: nextMode, enabled: nextEnabled }) {
    setSaveError('')
    await saveControllerRef.current?.save({ mode: nextMode, enabled: nextEnabled })
  }

  /** 切换遮蔽模式；总开关关闭或未变化则跳过。 */
  async function handleChange(nextMode) {
    if (!loaded || !enabled || nextMode === mode) return
    setMode(nextMode)
    if (nextMode === savedModeRef.current) return
    await persist({ mode: nextMode, enabled })
  }

  /** 切换卡包级总开关；与已保存值不同才保存。 */
  async function handleEnabledChange(nextEnabled) {
    if (!loaded || nextEnabled === enabled) return
    setEnabled(nextEnabled)
    if (nextEnabled === savedEnabledRef.current) return
    await persist({ mode, enabled: nextEnabled })
  }

  const options = [
    { value: 'random', label: t('deckSettings.maskModeRandom') },
    { value: 'full', label: t('deckSettings.maskModeFull') },
  ]
  const controlsDisabled = !loaded

  return (
    <Card
      raised
      outline
      contentWrap={false}
      className="!mx-4 !my-0 overflow-hidden"
      header={(
        <div className="flex items-center gap-3">
          <h2 className="font-semibold">{t('deckSettings.maskMode')}</h2>
          <div className="ml-auto flex items-center gap-2">
            {savedMessage && <span className="text-sm text-app-success">{savedMessage} ✓</span>}
            <PluginSettingsLink pluginId="mask-mode" />
          </div>
        </div>
      )}
    >
      <p className="px-4 pb-3 pt-4 text-sm text-app-label-secondary">{t('deckSettings.maskModeDesc')}</p>
      <List nested strong className="!my-0">
        <ListItem
          title={t('deckSettings.maskModeEnabled')}
          subtitle={t('deckSettings.maskModeEnabledDesc')}
          after={(
            <Toggle
              checked={enabled}
              onChange={withGenericClick((event) => handleEnabledChange(event.target.checked))}
              disabled={controlsDisabled}
            >
              <span className="sr-only">{t('deckSettings.maskModeEnabled')}</span>
            </Toggle>
          )}
        />
        <ListItem
          innerChildren={(
            <Segmented strong rounded className="grid w-full grid-cols-2">
              {options.map((option) => (
                <SegmentedButton
                  key={option.value}
                  type="button"
                  active={mode === option.value}
                  disabled={controlsDisabled || !enabled}
                  onClick={withGenericClick(() => handleChange(option.value))}
                  className="min-h-10 min-w-0 px-2 text-sm disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
                >
                  {option.label}
                </SegmentedButton>
              ))}
            </Segmented>
          )}
        />
      </List>

      {saveError && <p className="px-4 pb-4 pt-3 text-sm text-app-danger">{saveError}</p>}
    </Card>
  )
}
