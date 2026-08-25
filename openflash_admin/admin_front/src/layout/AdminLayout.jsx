import { Button, Card, Navbar, Toggle } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'

const NAV_ITEMS = [
  { path: '/overview', labelKey: 'nav.overview' },
  { path: '/users', labelKey: 'nav.users' },
  { path: '/platform-ai', labelKey: 'nav.platformAi' },
]

export default function AdminLayout({ admin, children, dark, onToggleTheme, onLogout }) {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <main className="admin-page">
      <div className="admin-content-frame">
        <Navbar
          title={<h1>{t('app.title')}</h1>}
          colors={{
            bgIos: 'bg-transparent dark:bg-transparent',
            bgMaterial: 'bg-transparent dark:bg-transparent',
          }}
        />

        <div className="admin-shell-grid">
          <aside className="sticky top-4 grid gap-4">
            <Card raised outline className="!m-0">
              <p className="text-[17px] font-semibold text-app-label-primary">{admin.nickname || admin.username}</p>
              <p className="mt-1 text-sm text-app-label-secondary">@{admin.username}</p>
            </Card>

            <Card raised outline header={t('nav.menu')} className="!m-0">
              <nav className="grid gap-2" aria-label={t('nav.ariaLabel')}>
                {NAV_ITEMS.map(item => {
                  const active = location.pathname === item.path
                  return (
                    <Button
                      key={item.path}
                      rounded
                      tonal={active}
                      outline={!active}
                      onClick={() => navigate(item.path)}
                      className="justify-start"
                    >
                      {t(item.labelKey)}
                    </Button>
                  )
                })}
              </nav>
            </Card>

            <Card outline className="!m-0">
              <label className="flex items-center justify-between gap-3 text-sm text-app-label-secondary">
                {t('theme.dark')}
                <Toggle checked={dark} onChange={onToggleTheme} />
              </label>
              <Button className="mt-4" rounded clear onClick={onLogout}>{t('auth.logout')}</Button>
            </Card>
          </aside>

          <section className="admin-main-content">{children}</section>
        </div>
      </div>
    </main>
  )
}
