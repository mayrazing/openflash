import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join, dirname } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

function read(relativePath) {
  return readFileSync(join(__dirname, '..', relativePath), 'utf8')
}

test('Practice page does not import TTS implementation directly', () => {
  const source = [
    read('pages/Practice.jsx'),
    read('hooks/usePracticeSession.js'),
    read('hooks/usePracticePersistence.js'),
    read('hooks/usePracticeBootstrap.js'),
    read('hooks/usePracticeEngine.js'),
  ].join('\n')

  assert.doesNotMatch(source, /lib\/ttsUtils/)
  assert.doesNotMatch(source, /plugins\/tts/)
  assert.match(source, /practice:face-shown/)
})

test('core deck settings page does not own TTS auto speak UI', () => {
  const source = read('pages/DeckSettings.jsx')

  assert.doesNotMatch(source, /autoSpeakA/)
  assert.doesNotMatch(source, /autoSpeakB/)
  assert.match(source, /deck-settings\.sections/)
})

test('TTS plugin owns deck auto speak API', () => {
  const source = read('plugins/tts/api.js')

  assert.match(source, /getDeckTtsSettings/)
  assert.match(source, /saveDeckTtsSettings/)
  assert.match(source, /\/api\/plugins\/\$\{pluginId\}\/decks/)
})

test('global database module does not own AI card plugin API', () => {
  const source = read('db/database.js')

  assert.doesNotMatch(source, /checkAiCacheStatus/)
  assert.doesNotMatch(source, /ai-cache-status/)
})

test('app shell does not own AI card SSE events', () => {
  const source = read('App.jsx')

  assert.doesNotMatch(source, /AI_READY_EVENT/)
  assert.doesNotMatch(source, /ai-cache-ready/)
  assert.doesNotMatch(source, /aiCacheStatus/)
})

test('core settings pages do not own AI plugin UI', () => {
  const settings = read('pages/Settings.jsx')
  const deckSettings = read('pages/DeckSettings.jsx')

  assert.doesNotMatch(settings, /aiThink/)
  assert.doesNotMatch(deckSettings, /aiExplanation/)
  assert.doesNotMatch(deckSettings, /aiCompletion/)
  assert.match(deckSettings, /deck-settings\.sections/)
})

test('global database module does not own AI settings API', () => {
  const source = read('db/database.js')

  assert.doesNotMatch(source, /getAiConfig/)
  assert.doesNotMatch(source, /saveAiConfig/)
  assert.doesNotMatch(source, /getDeepSeekModels/)
  assert.doesNotMatch(source, /ai-config/)
  assert.doesNotMatch(source, /ai-settings/)
})

test('global AI provider settings are owned outside ai-card plugin', () => {
  const settings = read('pages/Settings.jsx')
  const pluginEntry = read('plugins/ai-card/index.js')
  const pluginApi = read('plugins/ai-card/api.js')
  const coreAiApi = read('ai/api.js')
  const coreAiApiTest = read('ai/api.test.js')

  assert.match(settings, /AiSettingsSection/)
  assert.doesNotMatch(pluginEntry, /AiCardSettingsSection|['"]settings\.sections['"]/)
  assert.doesNotMatch(pluginApi, /\/api\/settings\/ai-config/)
  assert.match(coreAiApi, /\/api\/settings\/ai-config/)
  assert.doesNotMatch(coreAiApiTest, /plugins\/ai-card/)
})

test('ai-collocations plugin is no longer registered', () => {
  const registry = read('plugins/registry.js')
  const dialog = read('plugins/ai-card/AiCardDialog.jsx')

  assert.doesNotMatch(registry, /ai-collocations/)
  assert.doesNotMatch(dialog, /ai-card\.collocations/)
})

test('plugin registry discovers plugin entry files without static concrete imports', () => {
  const registry = read('plugins/registry.js')

  assert.match(registry, /import\.meta\.glob/)
  assert.doesNotMatch(registry, /from '\.\/tts\/index/)
  assert.doesNotMatch(registry, /from '\.\/ai-card\/index/)
})

test('core does not statically import mask-mode plugin', () => {
  const source = [
    read('pages/Practice.jsx'),
    read('components/PracticeCard.jsx'),
    read('components/practice/PracticeActiveView.jsx'),
    read('hooks/usePracticeSession.js'),
    read('hooks/usePracticePersistence.js'),
    read('hooks/usePracticeBootstrap.js'),
    read('hooks/usePracticeEngine.js'),
    read('App.jsx'),
  ].join('\n')

  assert.doesNotMatch(source, /plugins\/mask-mode/)
  assert.doesNotMatch(source, /mask-mode/)
})

test('usePracticeBootstrap 是通用 prefetch 调度入口，不含 mask-mode 字眼', () => {
  const source = read('hooks/usePracticeBootstrap.js')

  // 必须接通用调度器
  assert.match(source, /runPracticePrefetch/)
  assert.match(source, /from '\.\.\/plugins\/practicePrefetch'/)
  // 严禁出现 mask-mode 任意大小写形态或路径
  assert.doesNotMatch(source, /mask-mode/i)
  assert.doesNotMatch(source, /plugins\/mask-mode/)
})

test('usePracticeBootstrap 不完整阻塞等待 practice prefetch', () => {
  const source = read('hooks/usePracticeBootstrap.js')

  assert.match(source, /PRACTICE_PREFETCH_START_BUDGET_MS/)
  assert.match(source, /Promise\.race/)
  assert.match(source, /waitForPracticePrefetchStartBudget/)
  assert.doesNotMatch(source, /await\s+runPracticePrefetch\(id\)/)
})
