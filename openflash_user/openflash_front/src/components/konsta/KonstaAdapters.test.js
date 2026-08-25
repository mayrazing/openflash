import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

function jsxFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return jsxFiles(path)
    return entry.name.endsWith('.jsx') ? [path] : []
  })
}

test('AppListInput prevents Konsta from receiving a null list item title', () => {
  const path = new URL('./AppListInput.jsx', import.meta.url)
  const source = existsSync(path) ? readFileSync(path, 'utf8') : ''

  assert.match(source, /title=\{title \?\? ''\}/)
})

test('AppListInput associates its visible label with the native form control', () => {
  const source = readFileSync(new URL('./AppListInput.jsx', import.meta.url), 'utf8')

  assert.match(source, /<label htmlFor=\{resolvedInputId\}>/)
  assert.match(source, /inputId=\{resolvedInputId\}/)
})

test('frontend imports ListInput through the safe adapter', () => {
  const sourceRoot = fileURLToPath(new URL('../../', import.meta.url))
  const directImports = jsxFiles(sourceRoot)
    .filter(path => !path.endsWith('AppListInput.jsx'))
    .filter((path) => /ListInput.*from 'konsta\/react'|from 'konsta\/react'.*ListInput/.test(readFileSync(path, 'utf8')))

  assert.deepEqual(directImports, [])
})

test('navbar back links use the native button adapter', () => {
  const sourceRoot = fileURLToPath(new URL('../../', import.meta.url))
  const adapter = readFileSync(new URL('./AppNavbarBackLink.jsx', import.meta.url), 'utf8')
  const directImports = jsxFiles(sourceRoot)
    .filter(path => !path.endsWith('AppNavbarBackLink.jsx'))
    .filter((path) => /NavbarBackLink.*from 'konsta\/react'|from 'konsta\/react'.*NavbarBackLink/.test(readFileSync(path, 'utf8')))

  assert.match(adapter, /component="button"/)
  assert.match(adapter, /type="button"/)
  assert.deepEqual(directImports, [])
})
