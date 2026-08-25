import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

test('Home page reuses card-list style batch selection for deck management', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  // 手势 handler 已由 hook 提供
  assert.match(source, /handlePointerDown/)
  assert.match(source, /batchDeleteConfirm/)
  // selectedIds 始终为数组（不再 null）
  assert.match(source, /t\('home\.selectedDecks', \{ count: selectedIds\.length \}\)/)
  assert.match(source, /t\('home\.deleteBatchConfirm', \{ count: selectedIds\.length \}\)/)
})

test('home navbar shows the OpenFlash logo in the leading area', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.match(source, /import openFlashLogo from '\.\.\/assets\/openflash-logo\.svg'/)
  assert.match(source, /left=\{<div className="flex w-16 items-center justify-center"><img[\s\S]*?src=\{openFlashLogo\}/)
  assert.match(source, /title=\{<h1>\{t\('home\.title'\)\}<\/h1>\}/)
})

test('home navigation actions keep Konsta semantic accent colors', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.doesNotMatch(source, /!text-white/)
  assert.match(source, /<Button small tonal rounded onClick=\{withGenericClick\(handleImportClick\)\}>/)
  assert.match(source, /<Button small tonal rounded onClick=\{withGenericClick\(enterEmptySelectMode\)\}>/)
  assert.match(source, /<Button small tonal rounded onClick=\{withGenericClick\(\(\) => navigate\('\/marketplace'\)\)\}>/)
})

test('home settings action shows a semantic keyboard focus ring', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.match(source, /onClick=\{withGenericClick\(isSelectMode \? exitSelectMode : \(\) => navigate\('\/settings'\)\)\}[\s\S]*?className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"/)
})

test('deck long-press surface disables native text selection', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.match(source, /data-deck-id=\{deck\.id\}[\s\S]*className="long-press-select-surface"/)
})

test('DeckCard marks its clickable card surface for pen activation bridge', () => {
  const source = readFileSync(join(__dirname, '../components/DeckCard.jsx'), 'utf8')

  assert.match(source, /data-pointer-activation/)
})

test('deck selection mode waits for a second long press before range selection', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.doesNotMatch(source, /handleDeckPointerDown/)
  assert.match(source, /useDragSelect/)
  assert.match(hook, /isSelectMode\)\s*\{\s*startDeferredDragSelect\(e, id\)\s*return\s*\}/)
})

test('deck first long press enters batch mode without starting range selection', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.match(source, /enterEmptySelectMode/)
  assert.match(source, /exitSelectMode/)
  assert.doesNotMatch(source, /function enterSelectMode\(deckId\)/)
  assert.match(hook, /function enterSelectMode\(id\)/)
})

test('deck selection mode keeps pending second long press when finger leaves the anchor deck', () => {
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.match(hook, /function startDeferredDragSelect\(event, id\)[\s\S]*event\.currentTarget\.setPointerCapture\(event\.pointerId\)/)
  assert.match(hook, /function handlePointerLeave\(event\)[\s\S]*if \(isSelectMode && longPressTimerRef\.current\) return/)
})

test('deck drag selection locks root scrolling until the finger is released', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.match(hook, /import \{ lockRootScrollForDragSelect, unlockRootScrollForDragSelect \} from '\.\.\/lib\/dragSelectScrollLock'/)
  assert.match(hook, /function startDragSelect\(event, id[\s\S]*lockRootScrollForDragSelect\(\)/)
  assert.match(hook, /function finishDragSelect\(event\)[\s\S]*unlockRootScrollForDragSelect\(\)/)
  assert.match(hook, /function exitSelectMode\(\)[\s\S]*unlockRootScrollForDragSelect\(\)/)
  assert.doesNotMatch(source, /lockRootScrollForDragSelect/)
})

test('long-press selection surface blocks native selection affordances', () => {
  const source = readFileSync(join(__dirname, '../index.css'), 'utf8')

  assert.match(source, /\.long-press-select-surface\s*\{[\s\S]*user-select:\s*none/)
  assert.match(source, /\.long-press-select-surface\s*\{[\s\S]*-webkit-user-select:\s*none/)
  assert.match(source, /\.long-press-select-surface\s*\{[\s\S]*-webkit-touch-callout:\s*none/)
})

test('home deck counts show mastered cards over all cards from deck list stats', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')
  const deckCard = readFileSync(join(__dirname, '../components/DeckCard.jsx'), 'utf8')

  assert.doesNotMatch(source, /getCardsByDeck/)
  assert.doesNotMatch(source, /summarizeHomeDeckCards/)
  assert.match(source, /masteredCount=\{Number\(deck\.masteredCount \?\? 0\)\}/)
  assert.match(source, /totalCount=\{Number\(deck\.activeCount \?\? 0\) \+ Number\(deck\.masteredCount \?\? 0\)\}/)
  assert.match(deckCard, /\{ mastered: masteredCount, total: totalCount \}/)
})

test('Home page no longer exposes the mastered collection entry', () => {
  const source = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.doesNotMatch(source, /navigate\('\/mastered'\)/)
  assert.doesNotMatch(source, /home\.masteredEntry/)
  assert.doesNotMatch(source, /home\.masteredCount/)
})
