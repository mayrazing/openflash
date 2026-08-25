import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const indexCss = readFileSync(new URL('../../index.css', import.meta.url), 'utf8')
const appPage = readFileSync(new URL('./AppPage.jsx', import.meta.url), 'utf8')
const modalShell = readFileSync(new URL('./ModalShell.jsx', import.meta.url), 'utf8')
const bottomActionBar = readFileSync(new URL('./BottomActionBar.jsx', import.meta.url), 'utf8')
const gitignore = readFileSync(new URL('../../../.gitignore', import.meta.url), 'utf8')
const responsiveE2e = readFileSync(new URL('../../../e2e/responsive-usability.spec.js', import.meta.url), 'utf8')

test('responsive tokens define safe areas and compact density', () => {
  assert.match(indexCss, /--app-safe-bottom:\s*max\(env\(safe-area-inset-bottom\),\s*0px\)/)
  assert.match(indexCss, /--app-page-x:\s*clamp\(/)
  assert.match(indexCss, /--app-control-y:\s*clamp\(/)
  assert.match(indexCss, /--practice-distribution-gap:/)
  assert.match(indexCss, /--practice-distribution-bottom-gap:/)
  assert.match(indexCss, /@media\s*\(max-height:\s*520px\)/)
  assert.match(indexCss, /@media\s*\(orientation:\s*landscape\)\s*and\s*\(max-height:\s*500px\)/)
})

test('AppPage owns max width, page padding, and bottom inset', () => {
  assert.match(appPage, /export default function AppPage/)
  assert.match(appPage, /max-w-lg/)
  assert.match(appPage, /var\(--app-page-x\)/)
  assert.match(appPage, /var\(--app-page-y\)/)
  assert.match(appPage, /bottomInset/)
})

test('AppPage forwards page-level event props to the main hit surface', () => {
  assert.match(appPage, /\.\.\.mainProps/)
  assert.match(appPage, /<main\s+\{\.\.\.mainProps\}/)
})

test('ModalShell provides scrollable safe-height panel', () => {
  assert.match(modalShell, /export function ModalPanel/)
  assert.match(modalShell, /export default function ModalShell/)
  assert.match(modalShell, /useModalBodyLock\(true\)/)
  assert.match(modalShell, /variant = 'dialog'/)
  assert.match(modalShell, /bottomSheet/)
  assert.match(modalShell, /panelOverflow = 'auto'/)
  assert.match(modalShell, /const maxHeight/)
  assert.match(modalShell, /maxHeight,/)
  assert.match(modalShell, /overflow-y-auto/)
  assert.match(modalShell, /z-\[70\]/)
  assert.match(modalShell, /bg-app-surface-primary/)
  assert.match(modalShell, /bg-app-overlay/)
})

test('BottomActionBar reserves safe bottom area', () => {
  assert.match(bottomActionBar, /export default function BottomActionBar/)
  assert.match(bottomActionBar, /tone = 'dark'/)
  assert.match(bottomActionBar, /surface/)
  assert.match(bottomActionBar, /fixed bottom-0 left-0 right-0/)
  assert.match(bottomActionBar, /var\(--app-safe-bottom\)/)
  assert.match(bottomActionBar, /data-dialog-bottom-boundary=\{dialogBoundary \? 'true' : undefined\}/)
})

test('Konsta toast enters and exits from the top safe area', () => {
  const toastBlock = indexCss.match(/\.k-toast\s*\{([^}]*)\}/)?.[1]
  const hiddenToastBlock = indexCss.match(/\.k-toast\.translate-y-full\s*\{([^}]*)\}/)?.[1]

  assert.match(toastBlock, /top:\s*calc\(var\(--k-safe-area-top\) \+ 1rem\)/)
  assert.match(toastBlock, /bottom:\s*auto/)
  assert.match(hiddenToastBlock, /transform:\s*translateY\(-100%\)/)
})

test('Playwright generated artifacts are ignored', () => {
  assert.match(gitignore, /^test-results\/$/m)
  assert.match(gitignore, /^playwright-report\/$/m)
})

test('responsive e2e keeps matrix small and avoids fixed waits', () => {
  assert.match(responsiveE2e, /interactionViewports/)
  assert.match(responsiveE2e, /gotoReady/)
  assert.doesNotMatch(responsiveE2e, /waitForLoadState\('networkidle'\)/)
  assert.doesNotMatch(responsiveE2e, /waitForTimeout\(650\)/)
})
