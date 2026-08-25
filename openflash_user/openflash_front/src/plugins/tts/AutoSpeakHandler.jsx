import { useEffect, useRef } from 'react'
import { isEnglish, ttsApi } from './api'
import { createAutoSpeakController } from './autoSpeakController.js'

/** 监听练习页卡面展示事件，由 TTS 插件决定是否发音。 */
export default function AutoSpeakHandler() {
  const controllerRef = useRef(null)
  if (!controllerRef.current) {
    controllerRef.current = createAutoSpeakController({
      getDeckTtsSettings: ttsApi.getDeckTtsSettings,
      isEnglish,
      speakText: ttsApi.speakText,
    })
  }

  useEffect(() => {
    const controller = controllerRef.current

    window.addEventListener('practice:face-shown', controller.handlePracticeFaceShown)
    window.addEventListener(ttsApi.TTS_DECK_SETTINGS_CHANGED_EVENT, controller.handleDeckSettingsChanged)
    return () => {
      window.removeEventListener('practice:face-shown', controller.handlePracticeFaceShown)
      window.removeEventListener(ttsApi.TTS_DECK_SETTINGS_CHANGED_EVENT, controller.handleDeckSettingsChanged)
    }
  }, [])

  return null
}
