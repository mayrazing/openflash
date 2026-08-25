import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

test('home toolbar has marketplace button navigating to /marketplace', () => {
  assert.match(src, /navigate\('\/marketplace'\)/)
  assert.match(src, /home\.marketplace/)
})
