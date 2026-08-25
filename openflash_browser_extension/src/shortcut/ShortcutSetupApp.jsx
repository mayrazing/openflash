import { useEffect } from 'react'
import { App, Button, Card, List, ListItem, Navbar } from 'konsta/react'
import { openBrowserShortcutSettings } from '../browserShortcutSettings.js'
import { resetLanguage, setLanguage, t } from '../i18n.js'

// eslint-disable-next-line react-refresh/only-export-components -- Export keeps the real click action testable.
export function openShortcutSettings(chromeApi) {
  return openBrowserShortcutSettings(chromeApi.tabs)
}

export default function ShortcutSetupApp({ chromeApi = chrome, navigatorApi = navigator }) {
  resetLanguage()
  const language = setLanguage(navigatorApi.language?.split('-')[0])

  useEffect(() => {
    document.documentElement.lang = language
    document.title = t('shortcutSetup.title')
  }, [language])

  return (
    <App
      className="min-h-screen bg-app-background text-app-label-primary"
      dark
      safeAreas={false}
      theme="ios"
    >
      <main className="mx-auto w-full max-w-[440px]">
        <Navbar title={t('shortcutSetup.title')} />
        <Card className="mx-3 my-3 bg-app-surface-primary">
          <p className="text-sm text-app-label-secondary">{t('shortcutSetup.description')}</p>
          <List className="my-3" nested strong>
            <ListItem
              title={t('shortcutSetup.importDefault')}
              after={<kbd className="text-app-label-secondary">Alt+Shift+D</kbd>}
            />
            <ListItem
              title={t('shortcutSetup.manualCard')}
              after={<kbd className="text-app-label-secondary">Alt+Shift+A</kbd>}
            />
          </List>
          <Button
            className="w-full bg-app-accent-fill text-app-on-accent"
            onClick={() => openShortcutSettings(chromeApi)}
          >
            {t('shortcutSetup.openButton')}
          </Button>
        </Card>
      </main>
    </App>
  )
}
