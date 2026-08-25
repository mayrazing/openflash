import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { List, ListItem, Range, Segmented, SegmentedButton, Toggle } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import { DEFAULT_DECK_SETTINGS, getDeck, getDeckSettings, saveDeckSettings, getReviewLoadProfiles } from '../db/database'
import { getErrorMessage } from '../lib/errorMessages.js'
import { getDeckCardSortOrder, saveDeckCardSortOrder } from '../lib/deckCardSortPreference.js'
import { withGenericClick } from '../lib/soundEngine'
import PluginSlot from '../plugins/pluginSlot'

function defaultReviewLoadProfiles(t) {
  return [
    { value: 'relaxed', label: t('deckSettings.reviewLoadRelaxed') },
    { value: 'standard', label: t('deckSettings.reviewLoadStandard') },
    { value: 'intensive', label: t('deckSettings.reviewLoadIntensive') },
  ]
}

const DISABLED_RANGE_COLORS = {
  valueBgIos: 'bg-app-disabled-fill',
  valueBgMaterial: 'bg-app-disabled-fill',
  thumbBgIos: 'bg-app-disabled-label',
  thumbBgMaterial: 'bg-app-disabled-label',
}

function reviewLoadProfileLabel(option, t) {
  if (option.value === 'relaxed') return t('deckSettings.reviewLoadRelaxed')
  if (option.value === 'standard') return t('deckSettings.reviewLoadStandard')
  if (option.value === 'intensive') return t('deckSettings.reviewLoadIntensive')
  return option.label
}

export default function DeckSettings() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()

  const [newCardsPerDay, setNewCardsPerDay] = useState(DEFAULT_DECK_SETTINGS.newCardsPerDay)
  const [retention, setRetention] = useState(Math.round(DEFAULT_DECK_SETTINGS.targetRetention * 100))
  const [reviewLoadProfile, setReviewLoadProfile] = useState(DEFAULT_DECK_SETTINGS.reviewLoadProfile)
  const [reviewLoadProfiles, setReviewLoadProfiles] = useState(() => defaultReviewLoadProfiles(t))
  const [cardSortOrder, setCardSortOrder] = useState('created_desc')
  const [duplicateSideAEnabled, setDuplicateSideAEnabled] = useState(DEFAULT_DECK_SETTINGS.duplicateSideAEnabled)
  const [duplicateSideBEnabled, setDuplicateSideBEnabled] = useState(DEFAULT_DECK_SETTINGS.duplicateSideBEnabled)

  const [deckName, setDeckName] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [savedMessage, setSavedMessage] = useState('')
  const [settingsSaveError, setSettingsSaveError] = useState('')

  const savedSettingsRef = useRef(null)
  const autoSaveTimerRef = useRef(null)
  const saveNoticeTimerRef = useRef(null)
  const saveInFlightRef = useRef(false)
  const pendingSaveRef = useRef(null)
  const currentDeckIdRef = useRef(id)
  const settingsRequestVersionRef = useRef(0)
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    currentDeckIdRef.current = id
    let cancelled = false
    settingsRequestVersionRef.current += 1
    pendingSaveRef.current = null
    saveInFlightRef.current = false
    savedSettingsRef.current = null
    clearTimeout(autoSaveTimerRef.current)
    clearTimeout(saveNoticeTimerRef.current)
    setSavedMessage('')
    setSettingsSaveError('')
    setLoaded(false)
    setCardSortOrder(getDeckCardSortOrder(id))

    getDeckSettings(id).then((s) => {
      if (cancelled) return
      setNewCardsPerDay(s.newCardsPerDay)
      setRetention(Math.round(s.targetRetention * 100))
      setReviewLoadProfile(s.reviewLoadProfile ?? 'standard')
      setDuplicateSideAEnabled(s.duplicateSideAEnabled ?? true)
      setDuplicateSideBEnabled(s.duplicateSideBEnabled ?? false)
      const snap = toPayload({
        newCardsPerDay: s.newCardsPerDay,
        retention: Math.round(s.targetRetention * 100),
        reviewLoadProfile: s.reviewLoadProfile ?? 'standard',
        duplicateSideAEnabled: s.duplicateSideAEnabled ?? true,
        duplicateSideBEnabled: s.duplicateSideBEnabled ?? false,
      })
      savedSettingsRef.current = snap
      setLoaded(true)
    }).catch((error) => {
      if (!cancelled) setSettingsSaveError(getErrorMessage(error?.code))
    })
    getDeck(id).then((d) => {
      if (!cancelled) setDeckName(d.name)
    }).catch(() => { })
    getReviewLoadProfiles()
      .then((profiles) => {
        if (!cancelled && Array.isArray(profiles) && profiles.length > 0) {
          setReviewLoadProfiles(profiles)
        }
      })
      .catch(() => { })

    return () => {
      cancelled = true
      mountedRef.current = false
      clearTimeout(autoSaveTimerRef.current)
      clearTimeout(saveNoticeTimerRef.current)
    }
  }, [id])

  function toPayload({
    newCardsPerDay,
    retention,
    reviewLoadProfile,
    duplicateSideAEnabled,
    duplicateSideBEnabled,
  }) {
    return {
      newCardsPerDay: Number(newCardsPerDay),
      targetRetention: retention / 100,
      reviewLoadProfile,
      duplicateSideAEnabled,
      duplicateSideBEnabled,
    }
  }

  function isSame(a, b) {
    if (!a || !b) return false
    return a.newCardsPerDay === b.newCardsPerDay
      && a.targetRetention === b.targetRetention
      && a.reviewLoadProfile === b.reviewLoadProfile
      && a.duplicateSideAEnabled === b.duplicateSideAEnabled
      && a.duplicateSideBEnabled === b.duplicateSideBEnabled
  }

  /**
   * 展示顶部保存成功提示，并复用同一个自动隐藏计时器。
   */
  function showSavedNotice(message) {
    if (!mountedRef.current) return
    setSavedMessage(message)
    clearTimeout(saveNoticeTimerRef.current)
    saveNoticeTimerRef.current = setTimeout(() => setSavedMessage(''), 1500)
  }

  /**
   * 创建带合并队列的自动保存函数；切换卡包后旧保存结果不能影响当前页面。
   */
  function createQueuedSaver({
    pendingRef,
    inFlightRef,
    requestVersionRef,
    savedRef,
    saveFn,
    setError,
    fallbackError,
    successMessage,
  }) {
    return async function saveQueued(payload) {
      const requestDeckId = id
      const requestVersion = requestVersionRef.current
      pendingRef.current = payload
      if (inFlightRef.current) return
      inFlightRef.current = true
      let showSaved = false
      try {
        while (pendingRef.current
          && requestDeckId === currentDeckIdRef.current
          && requestVersion === requestVersionRef.current) {
          const next = pendingRef.current
          pendingRef.current = null
          if (mountedRef.current
            && requestDeckId === currentDeckIdRef.current
            && requestVersion === requestVersionRef.current) {
            setError('')
          }
          try {
            await saveFn(requestDeckId, next)
            if (!mountedRef.current
              || requestDeckId !== currentDeckIdRef.current
              || requestVersion !== requestVersionRef.current) {
              break
            }
            savedRef.current = next
            showSaved = true
          } catch (error) {
            if (!mountedRef.current
              || requestDeckId !== currentDeckIdRef.current
              || requestVersion !== requestVersionRef.current) {
              break
            }
            showSaved = false
            setSavedMessage('')
            setError(getErrorMessage(error?.code) || fallbackError)
          }
        }
        if (mountedRef.current
          && requestDeckId === currentDeckIdRef.current
          && requestVersion === requestVersionRef.current
          && showSaved) {
          showSavedNotice(successMessage)
        }
      } finally {
        if (requestDeckId === currentDeckIdRef.current && requestVersion === requestVersionRef.current) {
          inFlightRef.current = false
        }
      }
    }
  }

  const saveSettingsQueued = createQueuedSaver({
    pendingRef: pendingSaveRef,
    inFlightRef: saveInFlightRef,
    requestVersionRef: settingsRequestVersionRef,
    savedRef: savedSettingsRef,
    saveFn: saveDeckSettings,
    setError: setSettingsSaveError,
    fallbackError: '',
    successMessage: t('deckSettings.basicSaved'),
  })

  useEffect(() => {
    if (!loaded) return
    const payload = toPayload({
      newCardsPerDay,
      retention,
      reviewLoadProfile,
      duplicateSideAEnabled,
      duplicateSideBEnabled,
    })
    clearTimeout(autoSaveTimerRef.current)
    if (isSame(payload, savedSettingsRef.current)) return
    autoSaveTimerRef.current = setTimeout(() => saveSettingsQueued(payload), 400)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [newCardsPerDay, retention, reviewLoadProfile, duplicateSideAEnabled, duplicateSideBEnabled, loaded])

  useEffect(() => {
    if (!loaded) return
    saveDeckCardSortOrder(id, cardSortOrder)
  }, [id, cardSortOrder, loaded])

  const settingsControlsDisabled = !loaded
  const backLabel = t('common.back').replace(/^←\s*/, '')

  return (
    <AppPage contentClassName="!pt-0">
      <AppNavbar
        centerTitle={false}
        titleClassName="min-w-0 flex-1 overflow-hidden"
        title={<h1 className="truncate">{t('deckSettings.title', { name: deckName })}</h1>}
        left={(
          <NavbarBackLink
            showText
            text={backLabel}
            onClick={withGenericClick(() => navigate(`/deck/${id}`))}
          />
        )}
        right={savedMessage ? <span className="px-2 text-sm text-app-success">{savedMessage} ✓</span> : null}
      />

      <List inset strong outline className="!mb-5 !mt-3">

        {/* 卡片排序 */}
        <ListItem
          title={t('deckSettings.cardSortOrder')}
          subtitle={t('deckSettings.cardSortOrderDesc')}
          innerChildren={(
            <div className="mt-3">
              <SegmentedOptions
                options={[
                  { value: 'created_desc', label: t('deckSettings.cardSortNewestFirst') },
                  { value: 'created_asc', label: t('deckSettings.cardSortOldestFirst') },
                ]}
                value={cardSortOrder}
                disabled={settingsControlsDisabled}
                columns={2}
                onChange={setCardSortOrder}
              />
            </div>
          )}
        />

        {/* 每日新卡上限 */}
        <ListItem
          title={<label htmlFor="deck-new-cards">{t('deckSettings.newCardsPerDay')}</label>}
          subtitle={t('deckSettings.newCardsPerDayDesc')}
          innerChildren={(
            <div className="mt-3 flex items-center gap-3">
              <Range
                inputId="deck-new-cards"
                min={0}
                max={50}
                value={newCardsPerDay}
                disabled={settingsControlsDisabled}
                colors={settingsControlsDisabled ? DISABLED_RANGE_COLORS : undefined}
                onChange={(event) => setNewCardsPerDay(Number(event.target.value))}
                className="min-w-0 flex-1"
              />
              <span className={`w-12 text-right text-base font-semibold ${settingsControlsDisabled ? 'text-app-disabled-label' : 'text-app-accent'}`}>
                {t('deckSettings.newCardsPerDayUnit', { count: newCardsPerDay })}
              </span>
            </div>
          )}
        />

        {/* 学习强度 */}
        <ListItem
          title={t('deckSettings.reviewLoad')}
          subtitle={t('deckSettings.reviewLoadDesc')}
          innerChildren={(
            <div className="mt-3">
              <SegmentedOptions
                options={reviewLoadProfiles.map(option => ({ ...option, label: reviewLoadProfileLabel(option, t) }))}
                value={reviewLoadProfile}
                disabled={settingsControlsDisabled}
                columns={3}
                onChange={setReviewLoadProfile}
              />
            </div>
          )}
        />

        {/* 目标记忆留存率 */}
        <ListItem
          title={<label htmlFor="deck-retention">{t('deckSettings.targetRetention')}</label>}
          subtitle={t('deckSettings.targetRetentionDesc')}
          innerChildren={(
            <div className="mt-3 flex items-center gap-3">
              <Range
                inputId="deck-retention"
                min={70}
                max={97}
                value={retention}
                disabled={settingsControlsDisabled}
                colors={settingsControlsDisabled ? DISABLED_RANGE_COLORS : undefined}
                onChange={(event) => setRetention(Number(event.target.value))}
                className="min-w-0 flex-1"
              />
              <span className={`w-12 text-right text-base font-semibold ${settingsControlsDisabled ? 'text-app-disabled-label' : 'text-app-accent'}`}>{retention}%</span>
            </div>
          )}
        />

        {/* 去重检测 */}
        <ListItem
          title={t('deckSettings.dedup')}
          subtitle={t('deckSettings.dedupDesc')}
          innerChildren={(
            <div className="mt-3 flex gap-6">
              <div className="flex items-center gap-2">
                <span className="text-sm text-app-label-secondary">{t('common.sideA')}</span>
                <Toggle
                  checked={duplicateSideAEnabled}
                  onChange={withGenericClick((event) => setDuplicateSideAEnabled(event.target.checked))}
                  disabled={settingsControlsDisabled}
                >
                  <span className="sr-only">{t('common.sideA')}</span>
                </Toggle>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-sm text-app-label-secondary">{t('common.sideB')}</span>
                <Toggle
                  checked={duplicateSideBEnabled}
                  onChange={withGenericClick((event) => setDuplicateSideBEnabled(event.target.checked))}
                  disabled={settingsControlsDisabled}
                >
                  <span className="sr-only">{t('common.sideB')}</span>
                </Toggle>
              </div>
            </div>
          )}
        />

      </List>

      {settingsSaveError && <p className="mt-3 text-sm text-app-danger">{settingsSaveError}</p>}

      <PluginSlot slotName="deck-settings.sections" props={{ deckId: id, deckName }} deckId={id} />
    </AppPage>
  )
}

/**
 * 渲染设置页里的分段选项按钮。
 */
function SegmentedOptions({ options, value, disabled, columns, onChange }) {
  return (
    <Segmented
      strong
      rounded
      className="grid w-full"
      style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
    >
      {options.map((option) => (
        <SegmentedButton
          key={option.value}
          type="button"
          active={value === option.value}
          disabled={disabled}
          onClick={withGenericClick(() => onChange(option.value))}
          className="min-h-10 min-w-0 px-2 text-sm disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
        >
          {option.label}
        </SegmentedButton>
      ))}
    </Segmented>
  )
}
