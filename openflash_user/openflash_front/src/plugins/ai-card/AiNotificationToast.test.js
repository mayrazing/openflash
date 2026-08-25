import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const srcDir = join(__dirname, '..', '..')
const pluginPath = join(__dirname, 'AiNotificationToast.jsx')

test('ready AI toast schedules the same auto-hide path as other AI toasts', () => {
  const source = readFileSync(pluginPath, 'utf8')
  const showReadyBody = source.match(/function showReady\(event\) \{(?<body>[\s\S]*?)\n\s{4}\}/)?.groups.body

  assert.ok(showReadyBody, 'showReady function should exist')
  assert.match(showReadyBody, /readyTimersRef\.current\.hide = setTimeout\(\(\) => dismissReady\(id\), AI_TOAST_VISIBLE_MS\)/)
})

test('AI toast timings are read from the shared toast config', () => {
  const configPath = join(srcDir, 'config', 'toast.js')
  const source = readFileSync(pluginPath, 'utf8')

  assert.equal(existsSync(configPath), true)
  assert.match(source, /import \{ AI_TOAST_EXIT_MS, AI_TOAST_VISIBLE_MS \} from '\.\.\/\.\.\/config\/toast'/)
})

test('AI notifications render with Konsta toast and button components', () => {
  const source = readFileSync(pluginPath, 'utf8')

  assert.match(source, /\{ Button, Toast \} from 'konsta\/react'/)
  assert.match(source, /<Toast/)
  assert.doesNotMatch(source, /<button/)
})
