import { useState } from 'react'
import { Button, Card, List, Navbar } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import { RequestError } from '../api/request.js'
import AppListInput from '../components/AppListInput.jsx'
import { loginAdmin } from './api.js'

export default function AdminLogin({ onAuthenticated }) {
  const { t } = useTranslation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errorKey, setErrorKey] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (!username.trim() || !password) {
      setErrorKey('errors.requiredCredentials')
      return
    }

    setSubmitting(true)
    setErrorKey('')
    try {
      const admin = await loginAdmin(username.trim(), password)
      onAuthenticated(admin)
    } catch (error) {
      if (error instanceof RequestError && (error.status === 401 || error.code === 40002)) {
        setErrorKey('errors.invalidCredentials')
      } else if (error instanceof RequestError && error.code === 42902) {
        setErrorKey('errors.loginRateLimited')
      } else if (error instanceof RequestError && error.status === 403) {
        setErrorKey('errors.forbidden')
      } else {
        setErrorKey('errors.generic')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="admin-page">
      <div className="admin-login-frame">
        <Navbar
          title={<h1>{t('app.title')}</h1>}
          colors={{
            bgIos: 'bg-transparent dark:bg-transparent',
            bgMaterial: 'bg-transparent dark:bg-transparent',
          }}
        />

        <div className="mb-4 mt-3 px-1">
          <h2 className="text-2xl font-bold text-app-label-primary">{t('auth.title')}</h2>
          <p className="mt-2 text-sm text-app-label-tertiary">{t('auth.description')}</p>
        </div>

        <Card raised outline className="!m-0">
          <form className="space-y-4" onSubmit={submit}>
            <List strong outline className="!m-0">
              <AppListInput
                outline
                label={t('auth.username')}
                value={username}
                onChange={event => setUsername(event.target.value)}
                autoComplete="username"
              />
              <AppListInput
                outline
                label={t('auth.password')}
                type="password"
                value={password}
                onChange={event => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </List>

            {errorKey && (
              <div className="rounded-2xl border border-app-danger bg-app-danger-tonal px-4 py-3 text-sm text-app-danger" role="alert">
                {t(errorKey)}
              </div>
            )}

            <Button
              large
              rounded
              type="submit"
              disabled={submitting}
              className="app-primary-fill"
            >
              {submitting ? t('auth.loggingIn') : t('auth.login')}
            </Button>
          </form>
        </Card>
      </div>
    </main>
  )
}
