import { lazy, Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { App as KonstaApp } from 'konsta/react'
import {
  clearLocalAccountSession,
  clearStoredSessionInvalidationReason,
  getCurrentUser,
  getSettings,
  getStoredSessionInvalidationReason,
  logout,
  saveSettings,
  UnauthorizedError,
} from './db/database'
import {
  captureAuthWindowToken,
  isAuthWindowTokenCurrent,
  subscribeSessionInvalidation,
} from './auth/sessionInvalidation'
import i18n, { detectedLang } from './i18n'
import { ThemeContext, getSystemTheme } from './lib/theme'
import { setSoundEnabled } from './lib/soundEngine'
import useGlobalOverscrollGuard from './lib/useGlobalOverscrollGuard'
import useSwipeBack from './lib/useSwipeBack'
import { appError } from './lib/appLog'
import { PluginProvider } from './plugins/PluginContext'
import PluginSlot from './plugins/pluginSlot'
import { SseProvider } from './sse/SseContext'

const Home = lazy(() => import('./pages/Home'))
const DeckDetail = lazy(() => import('./pages/DeckDetail'))
const Practice = lazy(() => import('./pages/Practice'))
const Summary = lazy(() => import('./pages/Summary'))
const Settings = lazy(() => import('./pages/Settings'))
const Mastered = lazy(() => import('./pages/Mastered'))
const Auth = lazy(() => import('./pages/Auth'))
const DeckSettings = lazy(() => import('./pages/DeckSettings'))
const Marketplace = lazy(() => import('./pages/Marketplace'))

// 根据主题切换根节点样式。
function applyTheme(theme) {
  if (theme === 'dark') {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

// ── App ────────────────────────────────────────────────────────
export default function App() {
  const { t } = useTranslation()
  const [theme, setTheme] = useState('light')
  const [authStatus, setAuthStatus] = useState('loading')
  const [currentUser, setCurrentUser] = useState(null)
  const [sessionInvalidationReasonKey, setSessionInvalidationReasonKey] = useState(
    () => getStoredSessionInvalidationReason(),
  )

  useGlobalOverscrollGuard()

  function applyAnonymousTheme() {
    const anonymousTheme = getSystemTheme()
    setTheme(anonymousTheme)
    applyTheme(anonymousTheme)
  }

  useEffect(() => {
    const preventZoom = (e) => { if (e.touches.length > 1) e.preventDefault() }
    // gesturestart covers iOS Safari pinch before touchmove fires
    const preventGesture = (e) => e.preventDefault()
    document.addEventListener('touchmove', preventZoom, { passive: false })
    document.addEventListener('gesturestart', preventGesture, { passive: false })
    document.addEventListener('gesturechange', preventGesture, { passive: false })
    return () => {
      document.removeEventListener('touchmove', preventZoom)
      document.removeEventListener('gesturestart', preventGesture)
      document.removeEventListener('gesturechange', preventGesture)
    }
  }, [])

  useEffect(() => subscribeSessionInvalidation(invalidation => {
    clearLocalAccountSession(invalidation.reasonKey)
    setCurrentUser(null)
    setAuthStatus('anonymous')
    setSessionInvalidationReasonKey(invalidation.reasonKey)
    applyAnonymousTheme()
    i18n.changeLanguage(detectedLang)
  }), [])

  useEffect(() => {
    let cancelled = false
    const authWindowToken = captureAuthWindowToken()

    async function bootstrap() {
      try {
        const user = await getCurrentUser()
        if (cancelled || !isAuthWindowTokenCurrent(authWindowToken)) {
          return
        }
        setCurrentUser(user)
        setAuthStatus('authenticated')
        clearStoredSessionInvalidationReason()
        setSessionInvalidationReasonKey(null)

        const settings = await getSettings()
        if (cancelled || !isAuthWindowTokenCurrent(authWindowToken)) {
          return
        }
        const savedTheme = settings.theme ?? 'light'
        setTheme(savedTheme)
        applyTheme(savedTheme)
        setSoundEnabled(settings.soundEnabled ?? true)
        i18n.changeLanguage(settings.language ?? detectedLang)
      } catch (error) {
        if (cancelled || !isAuthWindowTokenCurrent(authWindowToken)) {
          return
        }
        if (error instanceof UnauthorizedError) {
          setCurrentUser(null)
          setAuthStatus('anonymous')
          applyAnonymousTheme()
          i18n.changeLanguage(detectedLang)
          return
        }

        appError(error?.code ?? 50000, '获取当前用户失败', error)
        setCurrentUser(null)
        setAuthStatus('anonymous')
        applyAnonymousTheme()
        i18n.changeLanguage(detectedLang)
      }
    }

    bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  // 切换当前页面主题，默认同时保存；设置页可选择只切主题，把保存交给统一队列。
  async function toggleTheme(options = {}) {
    const next = theme === 'light' ? 'dark' : 'light'
    setTheme(next)
    applyTheme(next)
    if (options.save !== false) {
      await saveSettings({ theme: next })
    }
    return next
  }

  // 登录或注册成功后，刷新当前用户和主题设置。
  async function handleAuthenticated(user, authWindowToken) {
    if (!isAuthWindowTokenCurrent(authWindowToken)) return false

    clearStoredSessionInvalidationReason()
    setSessionInvalidationReasonKey(null)
    setCurrentUser(user)
    setAuthStatus('authenticated')

    try {
      const settings = await getSettings()
      if (!isAuthWindowTokenCurrent(authWindowToken)) return false

      const savedTheme = settings.theme ?? 'light'
      setTheme(savedTheme)
      applyTheme(savedTheme)
      setSoundEnabled(settings.soundEnabled ?? true)
      i18n.changeLanguage(settings.language ?? detectedLang)
      return true
    } catch (error) {
      if (!isAuthWindowTokenCurrent(authWindowToken)) return false
      i18n.changeLanguage(detectedLang)
      throw error
    }
  }

  // 退出当前账号，并回到未登录状态。
  async function handleLogout() {
    await logout()
    clearAuthenticatedState()
  }

  // 清理前端登录态；改密接口已经在服务端销毁当前会话。
  function handlePasswordChanged() {
    clearAuthenticatedState()
  }

  function clearAuthenticatedState() {
    setCurrentUser(null)
    setAuthStatus('anonymous')
    applyAnonymousTheme()
    i18n.changeLanguage(detectedLang)
  }

  if (authStatus === 'loading') {
    return (
      <div className="flex h-full items-center justify-center bg-app-background text-sm text-app-label-secondary">
        {t('app.loadingAccount')}
      </div>
    )
  }

  return (
    <PluginProvider>
    <SseProvider enabled={authStatus === 'authenticated'}>
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <KonstaApp theme="ios" dark>
      <BrowserRouter>
        <SwipeBack />
        <div className="h-full bg-app-background transition-colors">
          <Suspense fallback={<div className="flex h-full items-center justify-center bg-app-background text-sm text-app-label-secondary">{t('common.loading')}</div>}>
          <Routes>
            <Route
              path="/auth"
              element={
                authStatus === 'authenticated'
                  ? <Navigate to="/" replace />
                  : (
                    <Auth
                      onAuthenticated={handleAuthenticated}
                      sessionInvalidationReasonKey={sessionInvalidationReasonKey}
                    />
                  )
              }
            />
            <Route path="/" element={<ProtectedRoute authStatus={authStatus}><Home /></ProtectedRoute>} />
            <Route path="/deck/:id" element={<ProtectedRoute authStatus={authStatus}><DeckDetail /></ProtectedRoute>} />
            <Route
              path="/deck/:id/practice"
              element={<ProtectedRoute authStatus={authStatus}><Practice /></ProtectedRoute>}
            />
            <Route
              path="/deck/:id/summary"
              element={<ProtectedRoute authStatus={authStatus}><Summary /></ProtectedRoute>}
            />
            <Route
              path="/deck/:id/settings"
              element={<ProtectedRoute authStatus={authStatus}><DeckSettings /></ProtectedRoute>}
            />
            <Route
              path="/settings"
              element={
                <ProtectedRoute authStatus={authStatus}>
                  <Settings
                    currentUser={currentUser}
                    onLogout={handleLogout}
                    onPasswordChanged={handlePasswordChanged}
                  />
                </ProtectedRoute>
              }
            />
            <Route
              path="/marketplace"
              element={<ProtectedRoute authStatus={authStatus}><Marketplace /></ProtectedRoute>}
            />
            <Route
              path="/mastered"
              element={<ProtectedRoute authStatus={authStatus}><Mastered /></ProtectedRoute>}
            />
            <Route path="*" element={<Navigate to={authStatus === 'authenticated' ? '/' : '/auth'} replace />} />
          </Routes>
          </Suspense>
          <PluginSlot slotName="app.global" />
        </div>
      </BrowserRouter>
      </KonstaApp>
    </ThemeContext.Provider>
    </SseProvider>
    </PluginProvider>
  )
}

// 挂载全局右滑返回手势。
function SwipeBack() {
  useSwipeBack()
  return null
}

// 拦截未登录访问，只允许已登录用户进入业务页面。
function ProtectedRoute({ authStatus, children }) {
  if (authStatus !== 'authenticated') {
    return <Navigate to="/auth" replace />
  }
  return children
}
