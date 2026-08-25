import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const componentPaths = [
  './CsvImportModal.jsx',
  './CardEditModal.jsx',
  './CardMoveModal.jsx',
  './PromptEditDialog.jsx',
  './PluginInstallDialog.jsx',
  '../plugins/ai-card/AiCardDialog.jsx',
]

test('shared confirm dialog uses the Konsta dialog adapter', () => {
  const source = readFileSync(new URL('./ConfirmDialog.jsx', import.meta.url), 'utf8')
  assert.match(source, /KonstaConfirmDialog/)
  assert.match(source, /confirmButtonClassName=\{confirmButtonClassName\}/)
  assert.doesNotMatch(source, /ModalShell/)
})

test('shared confirm dialog recognizes migrated and legacy destructive classes', () => {
  const source = readFileSync(new URL('./ConfirmDialog.jsx', import.meta.url), 'utf8')

  assert.match(
    source,
    /destructive=\{confirmButtonClassName\.includes\('danger'\)\s*\|\|\s*confirmButtonClassName\.includes\('red'\)\}/
  )
})

test('modal components use the Konsta dialog adapters', () => {
  for (const path of componentPaths) {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    assert.match(source, /Konsta(?:Dialog|Sheet)Shell/, `${path} should use a Konsta dialog adapter`)
    assert.doesNotMatch(source, /from ['"].*layout\/ModalShell/, `${path} should not use the legacy modal shell`)
    assert.doesNotMatch(source, /useModalBodyLock/, `${path} should let the Konsta adapter own scroll locking`)
    assert.doesNotMatch(source, /fixed inset-0 bg-black\/40 flex items-center justify-center z-50 px-4/, `${path} should not keep raw fixed modal shell`)
  }
})

test('Home rename dialog uses the Konsta dialog adapter', () => {
  const source = readFileSync(new URL('../pages/Home.jsx', import.meta.url), 'utf8')
  const adapter = readFileSync(new URL('./konsta/KonstaDialogShell.jsx', import.meta.url), 'utf8')

  assert.match(source, /KonstaDialogShell/)
  assert.doesNotMatch(source, /useModalBodyLock/)
  assert.match(adapter, /Dialog/)
  assert.match(adapter, /useModalBodyLock\(open\)/)
})

test('Konsta dialog adapters provide keyboard-accessible modal behavior', () => {
  for (const path of ['./konsta/KonstaDialogShell.jsx', './konsta/KonstaSheetShell.jsx']) {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')

    assert.match(source, /useAccessibleModal/)
    assert.match(source, /role="dialog"/)
    assert.match(source, /aria-modal="true"/)
  }
})

test('Konsta dialog shell forwards optional layout style', () => {
  const source = readFileSync(new URL('./konsta/KonstaDialogShell.jsx', import.meta.url), 'utf8')

  assert.match(source, /style=\{style\}/)
})

test('CSV import uses the Konsta textarea field', () => {
  const source = readFileSync(new URL('./CsvImportModal.jsx', import.meta.url), 'utf8')

  assert.match(source, /ListInput/)
  assert.match(source, /type="textarea"/)
  assert.doesNotMatch(source, /<textarea/)
})
