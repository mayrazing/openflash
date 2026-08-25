import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, List, ListItem, Toggle } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import AppListInput from '../components/konsta/AppListInput'
import { changePassword, getSettings, saveSettings, getLanguageOptions } from '../db/database'
import { getErrorMessage } from '../lib/errorMessages.js'
import { useTheme } from '../lib/theme'
import { setSoundEnabled, withGenericClick } from '../lib/soundEngine'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n.js'
import AiSettingsSection from '../ai/AiSettingsSection.jsx'
import PluginSlot from '../plugins/pluginSlot'

function buildSettingsPayload({ soundEnabled, theme, language }) {
    return { soundEnabled, theme, language }
}

function payloadFromSettings(settings) {
    return buildSettingsPayload({
        soundEnabled: settings.soundEnabled ?? true,
        theme: settings.theme ?? 'light',
        language: settings.language ?? 'en',
    })
}

function isSameSettingsPayload(left, right) {
    if (!left || !right) return false
    return left.soundEnabled === right.soundEnabled
        && left.theme === right.theme
        && left.language === right.language
}

const DEFAULT_LANGUAGE_OPTIONS = [
    { value: 'zh', label: '中文' },
    { value: 'en', label: 'English' },
    { value: 'fi', label: 'Suomi' },
    { value: 'de', label: 'Deutsch' },
]

export default function Settings({ currentUser, onLogout, onPasswordChanged }) {
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { theme, toggleTheme } = useTheme()
    const [soundEnabled, setSoundEnabledState] = useState(true)
    const [language, setLanguage] = useState('en')
    const [settingsLoaded, setSettingsLoaded] = useState(false)
    const [saved, setSaved] = useState(false)
    const [saveError, setSaveError] = useState('')
    const [logoutLoading, setLogoutLoading] = useState(false)
    const [logoutError, setLogoutError] = useState('')
    const [currentPassword, setCurrentPassword] = useState('')
    const [newPassword, setNewPassword] = useState('')
    const [confirmPassword, setConfirmPassword] = useState('')
    const [passwordLoading, setPasswordLoading] = useState(false)
    const [passwordError, setPasswordError] = useState('')
    const [languageOptions, setLanguageOptions] = useState(DEFAULT_LANGUAGE_OPTIONS)
    const [languageOptionsLoaded, setLanguageOptionsLoaded] = useState(false)
    const savedSettingsRef = useRef(null)
    const currentSoundEnabledRef = useRef(true)
    const currentSettingsRef = useRef(null)
    const soundLoadedRef = useRef(false)
    const autoSaveTimerRef = useRef(null)
    const saveNoticeTimerRef = useRef(null)
    const saveInFlightRef = useRef(false)
    const pendingSavePayloadRef = useRef(null)
    const pendingSaveShowSavedRef = useRef(false)
    const mountedRef = useRef(false)

    useEffect(() => {
        mountedRef.current = true
        let cancelled = false

        getSettings().then((s) => {
            if (cancelled) return
            const savedSoundEnabled = s.soundEnabled ?? true
            setLanguage(s.language ?? 'en')
            const savedPayload = payloadFromSettings(s)
            setSoundEnabledState(savedSoundEnabled)
            savedSettingsRef.current = savedPayload
            currentSettingsRef.current = savedPayload
            currentSoundEnabledRef.current = savedSoundEnabled
            soundLoadedRef.current = true
            setSettingsLoaded(true)
            setSoundEnabled(savedSoundEnabled)
        }).catch((error) => {
            if (!cancelled) setSaveError(getErrorMessage(error?.code))
        })

        return () => {
            cancelled = true
            mountedRef.current = false
            clearTimeout(autoSaveTimerRef.current)
            clearTimeout(saveNoticeTimerRef.current)
            const pendingPayload = currentSettingsRef.current
            if (soundLoadedRef.current && pendingPayload
                && !isSameSettingsPayload(pendingPayload, savedSettingsRef.current)) {
                persistSettings(pendingPayload, { showSaved: false })
            }
        }
    }, [])

    useEffect(() => {
        let cancelled = false
        getLanguageOptions().then((options) => {
            if (cancelled || !Array.isArray(options) || options.length === 0) return
            setLanguageOptions(options)
        }).catch(() => {}).finally(() => {
            if (!cancelled) setLanguageOptionsLoaded(true)
        })

        return () => {
            cancelled = true
        }
    }, [])

    async function persistSettings(payload, { showSaved = true } = {}) {
        pendingSavePayloadRef.current = payload
        pendingSaveShowSavedRef.current = pendingSaveShowSavedRef.current || showSaved
        if (saveInFlightRef.current) return

        saveInFlightRef.current = true
        let shouldShowSaved = false

        try {
            while (pendingSavePayloadRef.current) {
                const nextPayload = pendingSavePayloadRef.current
                const nextShowSaved = pendingSaveShowSavedRef.current
                pendingSavePayloadRef.current = null
                pendingSaveShowSavedRef.current = false

                if (mountedRef.current) {
                    setSaveError('')
                }

                try {
                    const savedSettings = await saveSettings(nextPayload)
                    savedSettingsRef.current = payloadFromSettings(savedSettings)
                    setSoundEnabled(currentSoundEnabledRef.current)
                    shouldShowSaved = shouldShowSaved || nextShowSaved
                } catch (error) {
                    shouldShowSaved = false
                    if (mountedRef.current) {
                        setSaveError(getErrorMessage(error?.code))
                    }
                }
            }

            if (mountedRef.current && shouldShowSaved) {
                setSaved(true)
                clearTimeout(saveNoticeTimerRef.current)
                saveNoticeTimerRef.current = setTimeout(() => setSaved(false), 1500)
            }
        } finally {
            saveInFlightRef.current = false
        }
    }

    useEffect(() => {
        if (!soundLoadedRef.current) return
        const payload = buildSettingsPayload({ soundEnabled, theme, language })
        currentSettingsRef.current = payload
        clearTimeout(autoSaveTimerRef.current)
        if (isSameSettingsPayload(payload, savedSettingsRef.current)) return
        autoSaveTimerRef.current = setTimeout(() => persistSettings(payload), 400)
    }, [soundEnabled, theme, language])

    function handleSoundEnabledChange(value) {
        if (!soundLoadedRef.current) return
        setSoundEnabledState(value)
        currentSoundEnabledRef.current = value
        setSoundEnabled(value)
    }

    function handleThemeChange() {
        toggleTheme({ save: false })
    }

    function handleLanguageChange(lang) {
        if (!settingsLoaded || !languageOptionsLoaded) return
        setLanguage(lang)
        i18n.changeLanguage(lang)
    }

    async function handleLogout() {
        setLogoutLoading(true)
        setLogoutError('')

        try {
            await onLogout()
            navigate('/auth', { replace: true })
        } catch (error) {
            setLogoutError(getErrorMessage(error?.code))
        } finally {
            setLogoutLoading(false)
        }
    }

    async function handlePasswordChange(event) {
        event.preventDefault()
        setPasswordError('')
        if (newPassword !== confirmPassword) {
            setPasswordError(t('settings.passwordMismatch'))
            return
        }

        setPasswordLoading(true)
        try {
            await changePassword(currentPassword, newPassword)
            onPasswordChanged()
            navigate('/auth', { replace: true, state: { passwordChanged: true } })
        } catch (error) {
            setPasswordError(getErrorMessage(error?.code))
        } finally {
            setPasswordLoading(false)
        }
    }

    const languageControlsDisabled = !settingsLoaded || !languageOptionsLoaded
    const backLabel = t('common.back').replace(/^←\s*/, '')

    return (
        <AppPage contentClassName="!pt-0">
            <AppNavbar
                title={<h1>{t('settings.title')}</h1>}
                left={(
                    <NavbarBackLink
                        showText
                        text={backLabel}
                        onClick={withGenericClick(() => navigate('/'))}
                    />
                )}
                right={saved ? <span className="px-2 text-sm text-app-success">{t('common.saved')}</span> : null}
            />

            <Card raised outline header={t('settings.account')} className="!mb-5 !mt-3">
                <p className="text-[17px] font-semibold">
                    {currentUser?.nickname || currentUser?.username || t('settings.notLoggedIn')}
                </p>
                <p className="mt-1 text-sm text-app-label-secondary">
                    @{currentUser?.username || '--'}
                </p>
            </Card>

            <Card raised outline className="!mb-5 !mt-0">
                <details>
                    <summary className="cursor-pointer text-[17px] font-semibold text-app-label-primary">
                        {t('settings.security')}
                        <span className="ml-2 text-sm font-normal text-app-label-secondary">
                            {t('settings.changePassword')}
                        </span>
                    </summary>
                    <form onSubmit={handlePasswordChange} className="mt-4 space-y-4">
                    <List strong className="!m-0">
                        <AppListInput
                            outline
                            inputId="settings-current-password"
                            label={t('settings.currentPassword')}
                            type="password"
                            value={currentPassword}
                            onChange={(event) => setCurrentPassword(event.target.value)}
                            clearButton={Boolean(currentPassword)}
                            onClear={() => setCurrentPassword('')}
                            autoComplete="current-password"
                            required
                            minLength={12}
                            maxLength={100}
                        />
                        <AppListInput
                            outline
                            inputId="settings-new-password"
                            label={t('settings.newPassword')}
                            type="password"
                            value={newPassword}
                            onChange={(event) => setNewPassword(event.target.value)}
                            clearButton={Boolean(newPassword)}
                            onClear={() => setNewPassword('')}
                            autoComplete="new-password"
                            required
                            minLength={12}
                            maxLength={100}
                        />
                        <AppListInput
                            outline
                            inputId="settings-confirm-password"
                            label={t('settings.confirmPassword')}
                            type="password"
                            value={confirmPassword}
                            onChange={(event) => setConfirmPassword(event.target.value)}
                            clearButton={Boolean(confirmPassword)}
                            onClear={() => setConfirmPassword('')}
                            autoComplete="new-password"
                            required
                            minLength={12}
                            maxLength={100}
                        />
                    </List>

                    {passwordError ? (
                        <div role="alert" className="rounded-2xl border border-app-danger bg-app-danger-tonal px-4 py-3 text-sm text-app-danger">
                            {passwordError}
                        </div>
                    ) : null}

                    <Button
                        large
                        rounded
                        type="submit"
                        disabled={passwordLoading}
                        className="app-primary-fill"
                    >
                        {passwordLoading ? t('settings.changingPassword') : t('settings.changePassword')}
                    </Button>
                    </form>
                </details>
            </Card>

            <List inset strong outline className="!mb-5 !mt-0">
                <ListItem
                    title={t('settings.sound')}
                    subtitle={t('settings.soundDesc')}
                    after={(
                        <Toggle
                            checked={soundEnabled}
                            onChange={withGenericClick((event) => handleSoundEnabledChange(event.target.checked))}
                            disabled={!settingsLoaded}
                        >
                            <span className="sr-only">{t('settings.sound')}</span>
                        </Toggle>
                    )}
                />

                <ListItem
                    title={t('settings.darkMode')}
                    subtitle={theme === 'dark' ? t('settings.darkModeDark') : t('settings.darkModeLight')}
                    after={(
                        <Toggle
                            checked={theme === 'dark'}
                            onChange={withGenericClick(handleThemeChange)}
                        >
                            <span className="sr-only">{t('settings.darkMode')}</span>
                        </Toggle>
                    )}
                />

                <ListItem
                    title={t('settings.language')}
                    innerChildren={(
                        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
                            {languageOptions.map(({ value, label }) => (
                                <Button
                                    key={value}
                                    small
                                    rounded
                                    tonal={language === value}
                                    outline={language !== value}
                                    disabled={languageControlsDisabled}
                                    onClick={withGenericClick(() => handleLanguageChange(value))}
                                    className="min-h-11"
                                >
                                    {label}
                                </Button>
                            ))}
                        </div>
                    )}
                />
            </List>

            {saveError ? (
                <p className="mx-4 mt-3 text-sm text-app-danger">{saveError}</p>
            ) : null}

            <AiSettingsSection />
            <PluginSlot slotName="settings.sections" />

            <div className="px-4">
                <Button
                    large
                    outline
                    rounded
                    onClick={withGenericClick(handleLogout)}
                    disabled={logoutLoading}
                    className="k-color-brand-danger mt-3"
                >
                    {logoutLoading ? t('settings.loggingOut') : t('settings.logout')}
                </Button>
            </div>

            {logoutError ? (
                <p className="mx-4 mt-3 text-sm text-app-danger">{logoutError}</p>
            ) : null}
        </AppPage>
    )
}
