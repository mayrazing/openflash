import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, Card, List, Segmented, SegmentedButton } from 'konsta/react'
import { login, register } from '../db/database'
import {
  beginAuthAttempt,
  getActiveSessionInvalidation,
  isAuthWindowTokenCurrent,
} from '../auth/sessionInvalidation'
import { playGenericClick, withGenericClick } from '../lib/soundEngine'
import { getErrorMessage } from '../lib/errorMessages'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import ListInput from '../components/konsta/AppListInput'

export default function Auth({ onAuthenticated, sessionInvalidationReasonKey }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // 提交登录或注册表单，并在成功后进入首页。
  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    const authWindowToken = beginAuthAttempt()

    try {
      const user = mode === 'login'
        ? await login(username, password)
        : await register(username, password, nickname)
      const authenticated = await onAuthenticated(user, authWindowToken)
      if (authenticated) navigate('/', { replace: true })
    } catch (submitError) {
      if (isAuthWindowTokenCurrent(authWindowToken) && !getActiveSessionInvalidation()) {
        setError(getErrorMessage(submitError.code))
      }
    } finally {
      setSubmitting(false)
    }
  }

  // 切换登录和注册模式，并清理错误提示。
  function switchMode(nextMode) {
    setMode(nextMode)
    setError('')
  }

  return (
    <AppPage contentClassName="!pt-0">
      <AppNavbar title={<h1>Pick Word</h1>} />

      <div className="px-4">
        <div className="mb-4 mt-3 px-1">
          <h2 className="text-2xl font-bold text-app-label-primary">
            {mode === 'login' ? t('auth.loginTitle') : t('auth.registerTitle')}
          </h2>
          <p className="mt-2 text-sm text-app-label-tertiary">{t('auth.subtitle')}</p>
        </div>

        <Card raised outline className="!m-0">
          <Segmented strong rounded className="mb-5" role="tablist">
            <SegmentedButton
              type="button"
              active={mode === 'login'}
              role="tab"
              aria-selected={mode === 'login'}
              onClick={withGenericClick(() => switchMode('login'))}
              className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"
            >
              {t('auth.loginTab')}
            </SegmentedButton>
            <SegmentedButton
              type="button"
              active={mode === 'register'}
              role="tab"
              aria-selected={mode === 'register'}
              onClick={withGenericClick(() => switchMode('register'))}
              className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"
            >
              {t('auth.registerTab')}
            </SegmentedButton>
          </Segmented>

          <form onSubmit={handleSubmit} className="space-y-4">
            {location.state?.passwordChanged ? (
              <div role="status" className="rounded-2xl border border-app-success bg-app-success-tonal px-4 py-3 text-sm text-app-success">
                {t('auth.passwordChanged')}
              </div>
            ) : null}

            {sessionInvalidationReasonKey ? (
              <div role="alert" className="rounded-2xl border border-app-danger bg-app-danger-tonal px-4 py-3 text-sm text-app-danger">
                {t(sessionInvalidationReasonKey)}
              </div>
            ) : null}

            <List strong outline className="!m-0">
              <ListInput
                outline
                inputId="auth-username"
                label={t('auth.username')}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder={t('auth.usernamePlaceholder')}
                autoComplete="username"
              />
              <ListInput
                outline
                inputId="auth-password"
                label={t('auth.password')}
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder={t('auth.passwordPlaceholder')}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={mode === 'register' ? 12 : undefined}
              />

              {mode === 'register' ? (
                <ListInput
                  outline
                  inputId="auth-nickname"
                  label={t('auth.nickname')}
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  placeholder={t('auth.nicknamePlaceholder')}
                  autoComplete="nickname"
                />
              ) : null}
            </List>

            {error ? (
              <div role="alert" className="rounded-2xl border border-app-danger bg-app-danger-tonal px-4 py-3 text-sm text-app-danger">
                {error}
              </div>
            ) : null}

            <Button
              large
              rounded
              type="submit"
              onClick={playGenericClick}
              disabled={submitting}
              className="app-primary-fill"
            >
              {submitting ? t('auth.submitting') : mode === 'login' ? t('auth.submitLogin') : t('auth.submitRegister')}
            </Button>
          </form>
        </Card>
      </div>
    </AppPage>
  )
}
