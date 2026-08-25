import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { installPointerActivationBridge } from './lib/pointerActivationBridge'
import './index.css'

installPointerActivationBridge()

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
