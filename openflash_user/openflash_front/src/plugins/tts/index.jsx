import SpeakButton from './SpeakButton'
import TtsSettingsSection from './TtsSettingsSection'
import DeckTtsSettingsSection from './DeckTtsSettingsSection'
import TtsGlobal from './TtsGlobal'

export default {
  id: 'tts',
  slots: {
    'app.global': { component: TtsGlobal, order: 20 },
    'card.actions': { component: SpeakButton, order: 20 },
    'practice.card.actions': { component: SpeakButton, order: 20 },
    'settings.sections': { component: TtsSettingsSection, order: 20 },
    'deck-settings.sections': { component: DeckTtsSettingsSection, order: 20 },
  },
}
