import { createRoot } from 'react-dom/client'
import ShortcutSetupApp from './shortcut/ShortcutSetupApp.jsx'
import './ui/app.css'
import { watchSystemTheme } from './ui/systemTheme.js'

watchSystemTheme(document.documentElement)
createRoot(document.getElementById('app')).render(<ShortcutSetupApp />)
