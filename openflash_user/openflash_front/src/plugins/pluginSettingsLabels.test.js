import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const settingsPanels = [
  ['ai-card/DeckAiSettingsSection.jsx', 'ai-card'],
  ['tts/DeckTtsSettingsSection.jsx', 'tts'],
  ['mask-mode/DeckMaskModeSettingsSection.jsx', 'mask-mode'],
]

test('每个插件设置面板右上角显示可点击的对应插件名称', () => {
  for (const [relativePath, pluginId] of settingsPanels) {
    const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
    assert.match(source, /PluginSettingsLink/, relativePath)
    assert.match(source, new RegExp(`<PluginSettingsLink\\s+pluginId=["']${pluginId}["']\\s*\\/>`), relativePath)
  }
})

test('全局 AI provider 设置不显示 ai-card 插件链接', () => {
  const source = readFileSync(new URL('../ai/AiSettingsSection.jsx', import.meta.url), 'utf8')
  assert.doesNotMatch(source, /PluginSettingsLink/)
})

test('插件名称链接携带 pluginId 跳转插件市场', () => {
  const source = readFileSync(new URL('PluginSettingsLink.jsx', import.meta.url), 'utf8')
  assert.match(source, /to=\{`\/marketplace\?plugin=\$\{encodeURIComponent\(pluginId\)\}`\}/)
  assert.match(source, /plugins\.\$\{pluginId\}\.name/)
})
