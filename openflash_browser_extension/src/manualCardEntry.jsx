import './manualCardEditor.js'
import './manualCardImageProcessor.js'
import './manualCardSave.js'
import { createManualCardDialog } from './content/createManualCardDialog.jsx'
import { consumeManualCardWindowContext } from './manualCardWindow.js'
import './ui/app.css'
import './ui/manualCardPage.css'
import { watchSystemTheme } from './ui/systemTheme.js'

watchSystemTheme(document.documentElement)

async function startManualCardPage() {
  const context = await consumeManualCardWindowContext({
    search: window.location.search,
    storageSession: chrome.storage.session,
  })

  if (!context) {
    window.close()
    return
  }
  document.title = context.labels['manualCard.title'] || 'OpenFlash'
  const dialog = createManualCardDialog({ onClosed: () => window.close() })
  await dialog.open(context)
}

startManualCardPage().catch(() => window.close())
