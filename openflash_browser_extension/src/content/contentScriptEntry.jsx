import { createPageNotification } from './createPageNotification.jsx'
import { installContentScriptOnce } from './contentScriptRuntime.js'

installContentScriptOnce({
  createPageNotification,
})
