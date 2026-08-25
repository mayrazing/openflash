import AutoSpeakHandler from './AutoSpeakHandler'
import TtsToast from './TtsToast'

/** 同时挂载自动朗读监听器和 TTS 错误 Toast。 */
export default function TtsGlobal() {
  return (
    <>
      <AutoSpeakHandler />
      <TtsToast />
    </>
  )
}
