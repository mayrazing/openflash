import { createRoot } from 'react-dom/client'
import PopupApp from './popup/PopupApp.jsx'
import './ui/app.css'
import { watchSystemTheme } from './ui/systemTheme.js'

watchSystemTheme(document.documentElement)
createRoot(document.getElementById('app')).render(<PopupApp />)
