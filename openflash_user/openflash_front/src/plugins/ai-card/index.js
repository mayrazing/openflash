import AiCardManager from './AiCardManager'
import DeckAiSettingsSection from './DeckAiSettingsSection'
import createAiOpenAction from './AiOpenAction'
import createPracticeAiOpenAction from './PracticeAiOpenAction'

export default {
  id: 'ai-card',
  slots: {
    'app.global': { component: AiCardManager, order: 10 },
    'deck-settings.sections': { component: DeckAiSettingsSection, order: 10 },
    'card.open-actions': { action: createAiOpenAction, order: 10 },
    'practice.card.open-actions': { action: createPracticeAiOpenAction, order: 10 },
  },
}
