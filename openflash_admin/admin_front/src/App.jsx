import { useEffect, useState } from 'react'
import { App as KonstaApp, Button, Card, List, ListItem, Preloader } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { getCurrentAdmin, logoutAdmin } from './auth/api.js'
import AdminLogin from './auth/AdminLogin.jsx'
import AdminLayout from './layout/AdminLayout.jsx'
import PlatformAiPage from './platform-ai/PlatformAiPage.jsx'
import UsersPage from './users/UsersPage.jsx'

function initialTheme() {
  const saved = globalThis.localStorage?.getItem('openflash-admin-theme')
  if (saved === 'dark' || saved === 'light') return saved
  return globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function App() {
  const { t } = useTranslation()
  const [admin, setAdmin] = useState(null)
  const [checking, setChecking] = useState(true)
  const [theme, setTheme] = useState(initialTheme)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    globalThis.localStorage?.setItem('openflash-admin-theme', theme)
  }, [theme])

  useEffect(() => {
    let active = true
    getCurrentAdmin()
      .then(currentAdmin => {
        if (active) setAdmin(currentAdmin)
      })
      .catch(() => {
        if (active) setAdmin(null)
      })
      .finally(() => {
        if (active) setChecking(false)
      })
    return () => {
      active = false
    }
  }, [])

  if (checking) {
    return (
      <KonstaApp theme="ios" dark={theme === 'dark'}>
        <main className="admin-page grid place-items-center">
          <div className="text-center text-app-label-secondary">
            <Preloader className="mx-auto" />
            <p className="mt-3 text-sm">{t('common.loading')}</p>
          </div>
        </main>
      </KonstaApp>
    )
  }

  return (
    <KonstaApp theme="ios" dark={theme === 'dark'}>
      <BrowserRouter>
        <Routes>
          <Route
            path="/login"
            element={admin ? <Navigate to="/overview" replace /> : <AdminLogin onAuthenticated={setAdmin} />}
          />
          <Route
            path="/*"
            element={admin ? (
              <AuthenticatedRoutes
                admin={admin}
                dark={theme === 'dark'}
                onToggleTheme={() => setTheme(current => current === 'dark' ? 'light' : 'dark')}
                onSignedOut={() => setAdmin(null)}
              />
            ) : <Navigate to="/login" replace />}
          />
        </Routes>
      </BrowserRouter>
    </KonstaApp>
  )
}

function AuthenticatedRoutes({ admin, dark, onToggleTheme, onSignedOut }) {
  const navigate = useNavigate()

  async function logout() {
    try {
      await logoutAdmin()
    } finally {
      onSignedOut()
      navigate('/login', { replace: true })
    }
  }

  return (
    <AdminLayout
      admin={admin}
      dark={dark}
      onToggleTheme={onToggleTheme}
      onLogout={logout}
    >
      <Routes>
        <Route path="overview" element={<OverviewPage />} />
        <Route path="users" element={<UsersPage currentAdminId={admin.id} />} />
        <Route path="platform-ai" element={<PlatformAiPage />} />
        <Route path="cli" element={<Navigate to="/platform-ai" replace />} />
        <Route path="codex" element={<Navigate to="/platform-ai" replace />} />
        <Route path="*" element={<Navigate to="overview" replace />} />
      </Routes>
    </AdminLayout>
  )
}

function PageHeading({ page }) {
  const { t } = useTranslation()

  return (
    <div className="mb-4 px-1">
      <h2 className="text-2xl font-bold text-app-label-primary">{t(`pages.${page}.title`)}</h2>
      <p className="mt-2 text-sm text-app-label-tertiary">{t(`pages.${page}.description`)}</p>
    </div>
  )
}

function OverviewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <>
      <PageHeading page="overview" />
      <div className="admin-responsive-grid admin-responsive-grid--overview">
        <List inset strong outline className="!m-0">
          <ListItem title={t('pages.users.title')} subtitle={t('pages.users.description')} />
          <ListItem title={t('pages.platformAi.title')} subtitle={t('pages.platformAi.description')} />
        </List>
        <Card raised outline header={t('pages.overview.actions')} className="!m-0">
          <div className="grid gap-3">
            <Button rounded outline onClick={() => navigate('/users')}>{t('nav.users')}</Button>
            <Button rounded outline onClick={() => navigate('/platform-ai')}>{t('nav.platformAi')}</Button>
          </div>
        </Card>
      </div>
    </>
  )
}
