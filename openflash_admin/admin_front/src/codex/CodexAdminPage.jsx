import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Dialog,
  DialogButton,
  List,
  ListItem,
  Preloader,
  Toggle,
} from 'konsta/react'
import { useTranslation } from 'react-i18next'
import {
  cancelCodexLogin,
  getCodexSnapshot,
  logoutCodexAccount,
  setCodexEnabled,
  startCodexLogin,
} from './api.js'
import { createCodexCoordinator } from './state.js'

const RUNTIME_INFO = {
  AVAILABLE: { tone: 'success' },
  NOT_INSTALLED: { tone: 'danger' },
  NOT_LOGGED_IN: { tone: 'warning' },
  DISABLED: { tone: 'neutral' },
  ERROR: { tone: 'danger' },
}
const LOGIN_INFO = {
  IDLE: { tone: 'neutral' },
  STARTING: { tone: 'accent' },
  PENDING: { tone: 'accent' },
  SUCCEEDED: { tone: 'success' },
  FAILED: { tone: 'danger' },
  EXPIRED: { tone: 'warning' },
  CANCELED: { tone: 'neutral' },
}
const ACTIVE_LOGIN_STATES = new Set(['STARTING', 'PENDING'])
const DEFAULT_API = {
  cancelLogin: cancelCodexLogin,
  getSnapshot: getCodexSnapshot,
  logoutAccount: logoutCodexAccount,
  setEnabled: setCodexEnabled,
  startLogin: startCodexLogin,
}
const TONES = {
  success: 'bg-app-success-tonal text-app-success',
  warning: 'bg-app-warning-tonal text-app-warning',
  danger: 'bg-app-danger-tonal text-app-danger',
  accent: 'bg-app-surface-secondary text-app-accent',
  neutral: 'bg-app-surface-secondary text-app-label-secondary',
}

function StatusBadge({ tone = 'neutral', children }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${TONES[tone]}`}>
      {children}
    </span>
  )
}

function PageHeading() {
  const { t } = useTranslation()
  return (
    <div className="mb-4 px-1">
      <h2 className="text-2xl font-bold text-app-label-primary">{t('pages.cli.title')}</h2>
      <p className="mt-2 text-sm text-app-label-tertiary">{t('pages.cli.description')}</p>
    </div>
  )
}

export default function CodexAdminPage({
  api = DEFAULT_API,
  clearSchedule = clearTimeout,
  embedded = false,
  schedule = setTimeout,
}) {
  const { t } = useTranslation()
  const [, render] = useState(0)
  const [selected, setSelected] = useState(!embedded)
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false)
  const [coordinator] = useState(() => createCodexCoordinator({
    api,
    clearSchedule,
    onChange: () => render(version => version + 1),
    schedule,
  }))
  const pageState = coordinator.getState()

  useEffect(() => {
    coordinator.mount()
    return () => coordinator.unmount()
  }, [coordinator])

  if (pageState.viewState === 'loading') {
    return (
      <>
        {!embedded && <PageHeading />}
        <Card raised outline className="!m-0 text-center">
          <Preloader className="mx-auto" />
          <p className="mt-3 text-sm text-app-label-secondary">{t('pages.cli.codex.loading')}</p>
        </Card>
      </>
    )
  }

  if (pageState.viewState === 'error') {
    return (
      <>
        {!embedded && <PageHeading />}
        <Card raised outline className="!m-0" role="alert">
          <p className="font-semibold text-app-danger">{t('pages.cli.codex.loadError')}</p>
          <p className="mt-2 text-sm text-app-label-secondary">{t('pages.cli.codex.loadErrorDescription')}</p>
          <Button className="mt-4" rounded outline onClick={() => coordinator.mount()}>
            {t('pages.cli.codex.retry')}
          </Button>
        </Card>
      </>
    )
  }

  const { snapshot } = pageState
  const { login } = snapshot
  const runtime = RUNTIME_INFO[snapshot.runtimeStatus]
  const runtimeAvailable = snapshot.runtimeStatus === 'AVAILABLE'
  const loginState = login.state === 'SUCCEEDED' && !runtimeAvailable ? 'IDLE' : login.state
  const loginInfo = LOGIN_INFO[loginState]
  const loginActive = ACTIVE_LOGIN_STATES.has(loginState)
  const unavailableForLogin = ['NOT_INSTALLED', 'ERROR'].includes(snapshot.runtimeStatus)
  const accountAvailable = runtimeAvailable
  const summaryType = loginActive ? 'login' : 'runtime'
  const summaryState = loginActive ? loginState : snapshot.runtimeStatus
  const summaryInfo = loginActive ? loginInfo : runtime

  function openVerification() {
    if (loginState !== 'PENDING' || !login.verificationUrl) return
    window.open(login.verificationUrl, '_blank', 'noopener,noreferrer')
  }

  function confirmAccountLogout() {
    setLogoutConfirmOpen(false)
    coordinator.logoutAccount()
  }

  return (
    <>
      {!embedded && <PageHeading />}

      <Card raised outline header={t('pages.cli.connectedTitle')} className="platform-ai-content-column !mx-0 !mb-5 !mt-0">
        <button
          type="button"
          aria-expanded={selected}
          aria-controls="codex-cli-details"
          className={`flex w-full items-center justify-between gap-4 rounded-2xl border-2 bg-app-surface-secondary px-4 py-3 text-left ${selected ? 'border-app-accent' : 'border-app-separator'}`}
          onClick={() => {
            setSelected(value => !value)
            setLogoutConfirmOpen(false)
          }}
        >
          <span>
            <span className="block font-semibold text-app-label-primary">{t('pages.cli.codex.name')}</span>
            <span className="mt-1 block text-sm text-app-label-secondary">{t('pages.cli.codex.description')}</span>
          </span>
          <StatusBadge tone={snapshot.enabled ? summaryInfo.tone : 'neutral'}>
            {snapshot.enabled
              ? t(`pages.cli.codex.${summaryType}.${summaryState}.label`)
              : t('pages.cli.codex.globalDisabled')}
          </StatusBadge>
        </button>
        <p className="mt-3 text-xs text-app-label-tertiary">{t('pages.cli.connectedHint')}</p>
      </Card>

      {selected && (
        <div id="codex-cli-details" className="platform-ai-content-column">
      <div className="mb-3 flex items-center justify-between gap-4 px-1">
        <div>
          <h3 className="text-xl font-bold text-app-label-primary">{t('pages.cli.codex.detailTitle')}</h3>
          <p className="mt-1 text-sm text-app-label-tertiary">{t('pages.cli.codex.currentDescription')}</p>
        </div>
        <StatusBadge tone="accent">{t('pages.cli.codex.current')}</StatusBadge>
      </div>

      {pageState.error && (
        <Card raised outline className="!mb-4 !mt-0" role="alert">
          <p className="text-sm font-semibold text-app-danger">
            {t(`pages.cli.codex.${pageState.error === 'toggle'
              ? 'updateError'
              : pageState.error === 'login'
                ? 'loginActionError'
                : pageState.error === 'logout'
                  ? 'logoutActionError'
                  : 'loadError'}`)}
          </p>
        </Card>
      )}

      <div className="admin-responsive-grid admin-responsive-grid--codex">
        <List inset strong outline className="!m-0">
          <ListItem
            title={t('pages.cli.codex.globalSwitch')}
            subtitle={t('pages.cli.codex.globalSwitchDescription')}
            after={(
              <Toggle
                checked={snapshot.enabled}
                disabled={pageState.toggleBusy || snapshot.runtimeStatus === 'ERROR'}
                aria-label={t('pages.cli.codex.globalSwitchAria')}
                onChange={event => coordinator.setEnabled(event.target.checked)}
              />
            )}
          />
          <ListItem
            title={t('pages.cli.codex.runtimeTitle')}
            subtitle={t(`pages.cli.codex.runtime.${snapshot.runtimeStatus}.description`)}
            after={(
              <StatusBadge tone={runtime.tone}>
                {t(`pages.cli.codex.runtime.${snapshot.runtimeStatus}.label`)}
              </StatusBadge>
            )}
          />
          <ListItem
            title={t('pages.cli.codex.changeTiming')}
            subtitle={t('pages.cli.codex.globalDelay', {
              seconds: snapshot.globalChangeMaxDelaySeconds,
            })}
          />
        </List>

        {loginActive && (
          <Card raised outline header={t('pages.cli.codex.waitingTitle')} className="!m-0">
            <p className="text-sm text-app-label-secondary">
              {t(`pages.cli.codex.login.${loginState}.message`)}
            </p>
            {loginState === 'PENDING' && (
              <>
                <div className="my-4 rounded-2xl bg-app-surface-secondary px-4 py-5 text-center">
                  <p className="text-xs text-app-label-tertiary">{t('pages.cli.codex.deviceCode')}</p>
                  <p className="mt-2 font-mono text-2xl font-semibold tracking-[0.2em] text-app-label-primary">
                    {login.userCode}
                  </p>
                </div>
                {login.verificationUrl && (
                  <Button rounded outline className="mb-3 w-full" onClick={openVerification}>
                    {t('pages.cli.codex.openVerification')}
                  </Button>
                )}
              </>
            )}
            <Button
              rounded
              clear
              className="w-full"
              disabled={pageState.loginBusy}
              onClick={() => coordinator.cancelLogin()}
            >
              {pageState.loginBusy && <Preloader className="mr-2" />}
              {t('pages.cli.codex.cancelLogin')}
            </Button>
          </Card>
        )}

        {!loginActive && accountAvailable && (
          <Card raised outline header={t('pages.cli.codex.accountTitle')} className="!m-0">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="font-semibold text-app-label-primary">{t('pages.cli.codex.accountComplete')}</p>
                <p className="mt-2 text-sm text-app-label-secondary">{t('pages.cli.codex.accountPrivacy')}</p>
              </div>
              <StatusBadge tone="success">
                {t(loginState === 'SUCCEEDED'
                  ? 'pages.cli.codex.login.SUCCEEDED.label'
                  : 'pages.cli.codex.runtime.AVAILABLE.label')}
              </StatusBadge>
            </div>
            <div className="mt-4 flex flex-wrap gap-3">
              <Button
                rounded
                outline
                disabled={pageState.refreshBusy || pageState.accountBusy}
                onClick={() => coordinator.refresh()}
              >
                {pageState.refreshBusy && <Preloader className="mr-2" />}
                {t('pages.cli.codex.refreshStatus')}
              </Button>
              <Button
                rounded
                outline
                colors={{
                  textIos: 'text-app-danger',
                  textMaterial: 'text-app-danger',
                  outlineBorderIos: 'border-app-danger',
                  outlineBorderMaterial: 'border-app-danger',
                }}
                disabled={pageState.accountBusy || pageState.refreshBusy}
                onClick={() => setLogoutConfirmOpen(true)}
              >
                {pageState.accountBusy && <Preloader className="mr-2" />}
                {t('pages.cli.codex.logoutAccount')}
              </Button>
            </div>
          </Card>
        )}

        {!loginActive && !accountAvailable && (
          <Card raised outline header={t('pages.cli.codex.loginTitle')} className="!m-0">
            <p className="text-sm text-app-label-secondary">{t('pages.cli.codex.loginOnlyHere')}</p>
            <p className={`mt-3 text-sm ${['FAILED', 'EXPIRED'].includes(loginState)
              ? 'text-app-danger'
              : 'text-app-label-secondary'}`}
            >
              {t(`pages.cli.codex.login.${loginState}.message`)}
            </p>
            <Button
              large
              rounded
              className="app-primary-fill mt-4"
              onClick={() => coordinator.startLogin()}
              disabled={unavailableForLogin || pageState.accountBusy
                || pageState.refreshBusy || pageState.loginBusy}
            >
              {(pageState.accountBusy || pageState.refreshBusy || pageState.loginBusy)
                && <Preloader className="mr-2" />}
              {t('pages.cli.codex.startLogin')}
            </Button>
          </Card>
        )}
      </div>
        </div>
      )}

      {selected && logoutConfirmOpen && (
        <Dialog
          opened
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="codex-logout-title"
          aria-describedby="codex-logout-description"
          onBackdropClick={() => setLogoutConfirmOpen(false)}
          title={<span id="codex-logout-title">{t('pages.cli.codex.logoutConfirmTitle')}</span>}
          content={(
            <p id="codex-logout-description">
              {t('pages.cli.codex.logoutConfirmBody')}
            </p>
          )}
          buttons={(
            <>
              <DialogButton onClick={() => setLogoutConfirmOpen(false)}>
                {t('pages.cli.codex.logoutCancel')}
              </DialogButton>
              <DialogButton
                strong
                colors={{
                  fillBgIos: 'bg-app-danger-fill active:opacity-80',
                  fillBgMaterial: 'bg-app-danger-fill active:opacity-80',
                  fillTextIos: 'text-white',
                  fillTextMaterial: 'text-white',
                }}
                onClick={confirmAccountLogout}
              >
                {t('pages.cli.codex.logoutAccount')}
              </DialogButton>
            </>
          )}
        />
      )}
    </>
  )
}
