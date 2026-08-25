import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

test('deck detail consumes one-shot practice day rollover state and shows dismissible modal', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /useLocation/)
  assert.match(detail, /practiceDayRolledOver/)
  assert.match(detail, /navigate\([\s\S]*replace:\s*true[\s\S]*state:\s*null/)
  assert.match(detail, /<KonstaDialogShell[\s\S]*onClose=\{closeDayRolloverNotice\}/)
  assert.match(detail, /t\('practice\.dayRolledOver'\)/)
  assert.match(detail, /t\('common\.close'\)/)
})

test('practice day rollover copy exists in every locale', () => {
  for (const locale of ['zh', 'en', 'fi', 'de']) {
    const messages = JSON.parse(readFileSync(join(__dirname, '..', 'locales', `${locale}.json`), 'utf8'))
    assert.equal(typeof messages.practice.dayRolledOver, 'string')
    assert.equal(typeof messages.practice.dayRolloverClearError, 'string')
  }
})

test('deck detail sticky controls auto collapse on upward page scroll and remain manually toggleable', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /const \[headerCollapsed, setHeaderCollapsed\] = useState\(false\)/)
  assert.match(detail, /const root = document\.getElementById\('root'\)/)
  assert.match(detail, /root\.addEventListener\('scroll', onScroll, \{ passive: true \}\)/)
  assert.match(detail, /root\.removeEventListener\('scroll', onScroll\)/)
  assert.match(detail, /shouldAutoCollapseDeckHeader\(\{[\s\S]*collapsibleHeight: collapsibleHeaderRef\.current\?\.scrollHeight \?\? 0/)
  assert.match(detail, /if \(nextScrollY <= 0\) \{\s*setHeaderCollapsed\(false\)/)
  assert.match(detail, /sticky top-0 z-30/)
  assert.match(detail, /aria-expanded=\{!headerCollapsed\}/)
  assert.match(detail, /left-1\/2/)
  assert.match(detail, /-translate-x-1\/2/)
  assert.match(detail, /h-12 w-32/)
  assert.match(detail, /grid-rows-\[0fr\]/)
  assert.match(detail, /grid-rows-\[1fr\]/)
  assert.match(detail, /transition-\[grid-template-rows,opacity\]/)
  assert.match(detail, /overflowAnchor: 'none'/)
  assert.doesNotMatch(detail, /!headerCollapsed && <>/)
  assert.match(detail, /deckDetail\.expandControls/)
  assert.match(detail, /deckDetail\.collapseControls/)
})

test('deck detail expands auto-collapsed controls outside the card list flow', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /const \[headerDetached, setHeaderDetached\] = useState\(false\)/)
  assert.match(detail, /setHeaderCollapsed\(true\)\s*setHeaderDetached\(true\)/)
  assert.match(detail, /headerDetached\s*\?\s*'absolute inset-x-0 top-full/)
})

test('deck detail bottom shortcut collapses controls before scheduling the bottom scroll', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const bottomShortcut = detail.match(/async function autoLoadToBottom\(\)[\s\S]*?(?=\n[ ]{2}\/\*\*\n[ ]{3}\* 右侧向上双箭头)/)?.[0] ?? ''

  assert.match(detail, /function queueScrollToBottom\(\) \{\s*setHeaderCollapsed\(true\)\s*setHeaderDetached\(true\)\s*setScrollToBottomPending\(true\)\s*\}/)
  assert.equal(bottomShortcut.match(/queueScrollToBottom\(\)/g)?.length, 3)
  assert.doesNotMatch(bottomShortcut, /setScrollToBottomPending\(true\)/)
})

test('deck detail commits the head page before scrolling to top and re-enabling forward pagination', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const exitTailToHead = detail.match(/async function exitTailToHead\(\)[\s\S]*?(?=\n[ ]{2}\/\*\*\n[ ]{3}\* 切换筛选条件)/)?.[0] ?? ''

  assert.match(detail, /import \{ useEffect, useLayoutEffect, useRef, useState \} from 'react'/)
  assert.match(detail, /const headJumpRef = useRef\(false\)/)
  assert.match(detail, /const \[scrollToTopPending, setScrollToTopPending\] = useState\(false\)/)
  assert.match(detail, /async function loadNextPage\(\) \{\s*if \(headJumpRef\.current \|\| loadingMore/)
  assert.match(exitTailToHead, /headJumpRef\.current = true[\s\S]*fetchPage\(0, \{ replace: true \}\)/)
  assert.match(exitTailToHead, /setWindowStart\(0\)\s*setScrollToTopPending\(true\)/)
  assert.doesNotMatch(exitTailToHead, /scrollTo\(\{ top: 0, behavior: 'smooth' \}\)/)
  assert.match(detail, /useLayoutEffect\(\(\) => \{\s*if \(!scrollToTopPending\) return[\s\S]*scrollTo\(\{ top: 0, behavior: 'auto' \}\)[\s\S]*headJumpRef\.current = false[\s\S]*setScrollToTopPending\(false\)/)
})

test('deck detail quick actions use the same spacing as home quick actions', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const home = readFileSync(join(__dirname, 'Home.jsx'), 'utf8')

  assert.match(home, /grid w-full grid-cols-3 gap-2/)
  assert.match(detail, /mb-4 grid grid-cols-3 gap-2/)
})

test('deck detail quick actions keep Konsta semantic accent colors', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /<Button small tonal rounded onClick=\{withGenericClick\(\(\) => navigate\(`\/deck\/\$\{id\}\/summary`\)\)\}>/)
  assert.match(detail, /<Button small tonal rounded onClick=\{withGenericClick\(\(\) => \{ setCsvOpen\(true\); setCsvResult\(null\) \}\)\}>/)
  assert.match(detail, /<Button small tonal rounded onClick=\{withGenericClick\(\(\) => navigate\(`\/deck\/\$\{id\}\/settings`\)\)\}>/)
})

test('deck detail start practice action has no filled background', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const startPracticeButton = detail.match(/<Button([\s\S]*?)\{t\('deckDetail\.startPractice'\)\}[\s\S]*?<\/Button>/)?.[0] ?? ''

  assert.match(startPracticeButton, /\sclear\s/)
})

test('deck detail start practice action keeps the semantic accent color', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const startPracticeButton = detail.match(/<Button([\s\S]*?)\{t\('deckDetail\.startPractice'\)\}[\s\S]*?<\/Button>/)?.[0] ?? ''

  assert.doesNotMatch(startPracticeButton, /!text-white/)
})

test('deck detail navbar places edge actions against the page sides', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /<AppNavbar[\s\S]*?innerClassName="!pl-0 !pr-0"[\s\S]*?left=\{/)
})

test('deck detail search uses one lightweight surface in dark mode', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const searchBlock = detail.match(/\{\/\* 搜索框 \*\/\}([\s\S]*?)\{\/\* 状态筛选栏 \*\/\}/)?.[1] ?? ''

  assert.match(searchBlock, /strongBgIos: 'bg-app-surface-primary'/)
  assert.match(searchBlock, /inputClassName="text-app-label-primary placeholder:text-app-label-tertiary"/)
  assert.match(searchBlock, /ring-app-control/)
  assert.match(searchBlock, /focus-within:ring-app-focus/)
  assert.doesNotMatch(searchBlock, /shadow-md/)
  assert.doesNotMatch(searchBlock, /<ListInput\s+outline/)
})

test('deck detail collapse trigger uses a shallow integrated notch below the title row', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /sticky top-0 z-30 mb-8/)
  assert.match(detail, /className="group absolute top-full left-1\/2 z-10 flex h-12 w-32/)
  assert.match(detail, /viewBox="0 0 128 48"/)
  assert.match(detail, /M0 0 C20 0 18 28 48 28 H80 C110 28 108 0 128 0/)
  assert.match(detail, /relative z-10 h-6 w-6 -translate-y-2/)
  assert.doesNotMatch(detail, /className="absolute -bottom-4 left-1\/2/)
})

test('deck detail header shadows preserve geometry and use the elevation token', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.equal(detail.match(/shadow-\[0_10px_18px_-18px_var\(--app-elevation-shadow\)\]/g)?.length, 2)
  assert.doesNotMatch(detail, /shadow-\[0_10px_18px_-18px\]/)
  assert.doesNotMatch(detail, /shadow-\[0_10px_18px_-18px_(?:currentColor|var\(--app-(?:overlay|label[\w-]*|disabled[\w-]*)\))\]/)
})

test('deck detail collapse controls copy exists in every locale', () => {
  for (const locale of ['zh', 'en', 'fi', 'de']) {
    const messages = JSON.parse(readFileSync(join(__dirname, '..', 'locales', `${locale}.json`), 'utf8'))
    assert.equal(typeof messages.deckDetail.expandControls, 'string')
    assert.equal(typeof messages.deckDetail.collapseControls, 'string')
  }
})

test('card long-press surface disables native text selection', () => {
  // 卡片列表已抽到 DeckCardList 组件，data-card-id 和 long-press-select-surface 应在其源码中
  const source = readFileSync(join(__dirname, '../components/DeckCardList.jsx'), 'utf8')
  assert.match(source, /data-card-id=\{card\.id\}[\s\S]*className=\{`long-press-select-surface/)
})

test('card selection mode waits for a second long press before range selection', () => {
  // 手势逻辑已抽到 useDragSelect hook，DeckDetail 不应再有旧实现
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.doesNotMatch(detail, /startDeferredCardDragSelect/)
  assert.match(detail, /useDragSelect/)
  assert.match(hook, /isSelectMode\)\s*\{\s*startDeferredDragSelect\(e, id\)\s*return\s*\}/)
})

test('card first long press enters batch mode without starting range selection', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.doesNotMatch(detail, /function enterSelectMode\(cardId\)/)
  assert.match(hook, /function enterSelectMode\(id\)/)
})

test('card selection mode keeps pending second long press when finger leaves the anchor card', () => {
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')

  assert.match(hook, /function startDeferredDragSelect\(event, id\)[\s\S]*event\.currentTarget\.setPointerCapture\(event\.pointerId\)/)
  assert.match(hook, /function handlePointerLeave\(event\)[\s\S]*if \(isSelectMode && longPressTimerRef\.current\) return/)
})

test('card drag selection locks root scrolling until the finger is released', () => {
  const hook = readFileSync(join(__dirname, '../hooks/useDragSelect.js'), 'utf8')
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  // lockRootScrollForDragSelect 已移到 hook，页面不应再引用
  assert.match(hook, /import \{ lockRootScrollForDragSelect, unlockRootScrollForDragSelect \} from '\.\.\/lib\/dragSelectScrollLock'/)
  assert.match(hook, /function startDragSelect\(event, id[\s\S]*lockRootScrollForDragSelect\(\)/)
  assert.match(hook, /function finishDragSelect\(event\)[\s\S]*unlockRootScrollForDragSelect\(\)/)
  assert.match(hook, /function exitSelectMode\(\)[\s\S]*unlockRootScrollForDragSelect\(\)/)
  assert.doesNotMatch(detail, /lockRootScrollForDragSelect/)
})

test('deck detail wires batch move modal and API result ids', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const bar = readFileSync(join(__dirname, '../components/SelectActionBar.jsx'), 'utf8')
  const modal = readFileSync(join(__dirname, '../components/CardMoveModal.jsx'), 'utf8')

  assert.match(detail, /moveCardsBatch/)
  assert.match(detail, /CardMoveModal/)
  assert.match(detail, /movedCardIds/)
  assert.doesNotMatch(detail, /useModalBodyLock\(!!\(editing \|\| csvOpen \|\| moveOpen\)\)/)
  assert.match(modal, /KonstaDialogShell/)
  assert.doesNotMatch(modal, /from ['"].*layout\/ModalShell/)
  assert.match(detail, /setMoveDecks\(\[\]\)/)
  assert.match(detail, /const moveTargetDecks = moveDecks\.filter\(deck => String\(deck\.id\) !== String\(id\)\)/)
  assert.match(detail, /decks=\{moveTargetDecks\}/)
  assert.match(detail, /setAllCards\(cards => cards\.filter[\s\S]*movedIdSet\.has\(String\(c\.id\)\)/)
  assert.match(detail, /prunePracticeSessionsInBackground\(\[\.\.\.movedIdSet\]\)/)
  assert.match(detail, /const nextSelectedIds = selectedIds\.filter[\s\S]*if \(nextSelectedIds\.length === 0\) \{[\s\S]*exitSelectMode\(\)/)
  assert.match(bar, /onBatchMove/)
  assert.match(bar, /deckDetail\.moveCards/)
  assert.match(bar, /<BottomActionBar innerClassName="flex items-center gap-2">/)
  assert.match(bar, /ml-auto flex min-w-0 items-center gap-2 overflow-x-auto whitespace-nowrap/)
  assert.match(modal, /moveCardsResult/)
})

test('deck detail sends created-time sort only through supported paged card requests', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')
  const database = readFileSync(join(__dirname, '../db/database.js'), 'utf8')

  assert.match(detail, /const \[sortOrder, setSortOrder\] = useState\(\(\) => getDeckCardSortOrder\(id\)\)/)
  assert.doesNotMatch(detail, /handleSortToggle/)
  assert.doesNotMatch(detail, /sortOrderRef/)
  assert.match(database, /params\.set\('sort', options\.sort\)/)
  assert.match(detail, /getDeckCardSortOrder\(id\)/)
  assert.match(detail, /setSortOrder\(nextSortOrder\)/)
  assert.match(detail, /function sortForCardListFilter\(nextFilter, nextSort\)/)
  assert.match(detail, /!nextFilter \|\| nextFilter === 'new' \|\| nextFilter === 'learning'/)
  assert.match(detail, /return null/)
  assert.match(detail, /sort: sortForCardListFilter\(filter, nextSortOrder\)/)
  assert.match(detail, /sort: sortForCardListFilter\(filter, sortOrder\)/)
})

test('deck detail search debounce uses the card sort loaded from settings', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /setTimeout\(\(\) => \{[\s\S]*reloadCardsWithFilter\(filter, kw\)/)
  assert.doesNotMatch(detail, /sortNewestFirst/)
  assert.doesNotMatch(detail, /sortOldestFirst/)
})

test('deck detail keeps learning in backend order and only sorts today or tomorrow by review time', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /filter === 'today' \|\| filter === 'tomorrow'/)
  assert.doesNotMatch(detail, /filter === 'today' \|\| filter === 'tomorrow' \|\| filter === 'learning'/)
})

test('deck detail only prepends new cards when descending unsliced list can contain it', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /function canPrependCreatedCard\(card\)/)
  assert.match(detail, /sortOrder !== 'created_desc'/)
  assert.match(detail, /if \(tailMode\) return false/)
  assert.match(detail, /if \(windowStartRef\.current !== 0\) return false/)
  assert.match(detail, /if \(canPrependCreatedCard\(newCard\)\) \{[\s\S]*setAllCards\(cards => \[newCard, \.\.\.cards\]\)/)
  assert.match(detail, /else \{[\s\S]*void silentRefreshCards\(\)/)
})

test('deck detail invalidates deferred visible cards after creating a card', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /if \(isDeferredFilter\(filter\)\) \{[\s\S]*resetDeferredFilterCards\(\)/)
  assert.match(detail, /if \(isDeferredFilter\(filter\)\) \{[\s\S]*void silentRefreshCards\(\)/)
})

test('deck detail ignores stale deferred card requests after invalidation', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /const deferredCardsTokenRef = useRef\(0\)/)
  assert.match(detail, /function resetDeferredFilterCards\(\)[\s\S]*deferredCardsTokenRef\.current \+= 1/)
  assert.match(detail, /async function loadTodayCardsForFilter\(\)[\s\S]*const deferredToken = deferredCardsTokenRef\.current[\s\S]*if \(isStaleDeferredLoad\(token, deferredToken\)\) return null[\s\S]*setTodayCards\(cards\)/)
  assert.match(detail, /async function loadTodayCardsForFilter\(\)[\s\S]*finally \{[\s\S]*if \(!isStaleDeferredLoad\(token, deferredToken\)\) \{[\s\S]*setTodayLoading\(false\)/)
  assert.match(detail, /async function ensureTodayCardsLoaded\(\)[\s\S]*await loadTodayCardsForFilter\(\)/)
  assert.match(detail, /async function loadTomorrowCardsForFilter\(\)[\s\S]*const deferredToken = deferredCardsTokenRef\.current[\s\S]*if \(isStaleDeferredLoad\(token, deferredToken\)\) return null[\s\S]*setTomorrowCards\(cards\)/)
  assert.match(detail, /async function loadTomorrowCardsForFilter\(\)[\s\S]*finally \{[\s\S]*if \(!isStaleDeferredLoad\(token, deferredToken\)\) \{[\s\S]*setTomorrowLoading\(false\)/)
  assert.match(detail, /async function ensureTomorrowCardsLoaded\(\)[\s\S]*await loadTomorrowCardsForFilter\(\)/)
})

test('deck detail select all all uses current filter scope', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /async function loadDeferredFilterCardsForSelectAll\(\)/)
  assert.match(detail, /async function handleSelectAllLoad\(\)[\s\S]*if \(isDeferredFilter\(filter\)\) \{[\s\S]*loadDeferredFilterCardsForSelectAll\(\)[\s\S]*filterCardsByKeyword\(deferredCards, keyword\)/)
  assert.match(detail, /async function handleSelectAllLoad\(\)[\s\S]*if \(!hasMore && !tailMode\) \{[\s\S]*setSelectedIds\(displayedCards\.map/)
  assert.match(detail, /state: toApiState\(filter\)/)
})

test('deck detail silent refresh resets card window back to the first page', () => {
  const detail = readFileSync(join(__dirname, 'DeckDetail.jsx'), 'utf8')

  assert.match(detail, /async function silentRefreshCards\(\)[\s\S]*setTailMode\(false\)/)
  assert.match(detail, /async function silentRefreshCards\(\)[\s\S]*setWindowStart\(0\)/)
})
