import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('./App.jsx', import.meta.url), 'utf8')
const layoutSource = await readFile(new URL('./layout/AdminLayout.jsx', import.meta.url), 'utf8')
const styleSource = await readFile(new URL('./index.css', import.meta.url), 'utf8')

test('anonymous routing exposes only login and authenticated routing uses admin layout', () => {
  assert.match(appSource, /path="\/login"/)
  assert.match(appSource, /admin\s*\?\s*<Navigate to="\/overview" replace \/>/)
  assert.match(appSource, /admin\s*\?\s*\(\s*<AuthenticatedRoutes/)
  assert.match(appSource, /:\s*<Navigate to="\/login" replace \/>/)
  assert.doesNotMatch(appSource, /register/i)
})

test('authenticated shell uses platform AI as the only AI management entry', () => {
  assert.match(appSource, /path="overview"/)
  assert.match(appSource, /path="users"/)
  assert.match(appSource, /path="platform-ai"/)
  assert.match(appSource, /path="cli" element={<Navigate to="\/platform-ai" replace \/>}/)
  assert.match(appSource, /path="codex" element={<Navigate to="\/platform-ai" replace \/>}/)
  assert.match(layoutSource, /path: '\/overview'/)
  assert.match(layoutSource, /path: '\/users'/)
  assert.match(layoutSource, /path: '\/platform-ai'/)
  assert.doesNotMatch(layoutSource, /path: '\/cli'/)
  assert.doesNotMatch(layoutSource, /mobile|drawer|hamburger/i)
})

test('authenticated content frame preserves the approved zero top padding', () => {
  assert.match(styleSource, /\.admin-content-frame\s*\{[^}]*padding-top:\s*0\s*;/s)
})

test('authenticated shell stacks when the page is narrower than the desktop layout', () => {
  assert.match(
    styleSource,
    /@media\s*\(max-width:\s*48rem\)[\s\S]*?\.admin-shell-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/,
  )
  assert.match(
    styleSource,
    /@media\s*\(max-width:\s*48rem\)[\s\S]*?\.admin-shell-grid\s*>\s*aside\s*\{[^}]*position:\s*static/,
  )
})

test('overview cards stack until their content area is wide enough', () => {
  assert.match(appSource, /className="admin-responsive-grid admin-responsive-grid--overview"/)
  assert.match(styleSource, /\.admin-main-content\s*\{[^}]*container-type:\s*inline-size/s)
  assert.match(
    styleSource,
    /@container\s*\(min-width:\s*44rem\)[\s\S]*?\.admin-responsive-grid--overview\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1\.4fr\)\s+minmax\(280px,\s*0\.8fr\)/,
  )
})
