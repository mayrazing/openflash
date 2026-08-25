import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import * as themeModule from './theme.js'

test('anonymous theme follows the current system appearance', () => {
  assert.equal(typeof themeModule.getSystemTheme, 'function')
  assert.equal(themeModule.getSystemTheme(() => ({ matches: true })), 'dark')
  assert.equal(themeModule.getSystemTheme(() => ({ matches: false })), 'light')
})

test('anonymous bootstrap, invalidation, and logout use the system appearance', () => {
  const source = readFileSync(new URL('../App.jsx', import.meta.url), 'utf8')

  assert.match(source, /import \{ ThemeContext, getSystemTheme \} from '\.\/lib\/theme'/)
  assert.match(source, /function applyAnonymousTheme\(\)[\s\S]*const anonymousTheme = getSystemTheme\(\)[\s\S]*setTheme\(anonymousTheme\)[\s\S]*applyTheme\(anonymousTheme\)/)
  assert.equal(source.match(/applyAnonymousTheme\(\)/g)?.length, 5)
  assert.doesNotMatch(source, /setTheme\('light'\)[\s\S]{0,80}applyTheme\('light'\)/)
})
