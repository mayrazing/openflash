import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useParams, useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, DialogButton, List, Preloader } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import ListInput from '../components/konsta/AppListInput'
import KonstaDialogShell from '../components/konsta/KonstaDialogShell'
import {
  getDeck,
  getCardsPageByDeck,
  getDeckCardStats,
  createCard,
  createCardsBatch,
  updateCard,
  deleteCard,
  resetCard,
  prunePracticeSessionForRemovedCard,
  prunePracticeSessionForRemovedCards,
  getToday,
  getDeckSettings,
  getTodayCardsByDeck,
  getTomorrowCardsByDeck,
  getAllDecks,
  moveCardsBatch,
} from '../db/database'
import ConfirmDialog from '../components/ConfirmDialog'
import { scrollElementIntoRootCenter } from '../lib/rootScroll'
import { useSideScrollHints } from '../lib/sideScrollHints'
import { withGenericClick } from '../lib/soundEngine'
import { getErrorMessage } from '../lib/errorMessages.js'
import { getDeckCardSortOrder } from '../lib/deckCardSortPreference.js'
import { shouldAutoCollapseDeckHeader } from '../lib/deckHeaderCollapse.js'
import {
  EMPTY_FACE, EMPTY_STATS, PAGE_SIZE, LOAD_ALL_STATUS,
  isDeferredFilter, toApiState, compareTodayCards,
  filterCardsByKeyword, replaceCardInLoadedList, parseCardTextRows,
} from '../lib/deckCardUtils.js'
import { useDragSelect } from '../hooks/useDragSelect.js'
import CardEditModal from '../components/CardEditModal'
import CsvImportModal from '../components/CsvImportModal'
import CardMoveModal from '../components/CardMoveModal'
import DeckFilterBar from '../components/DeckFilterBar'
import NewCardForm from '../components/NewCardForm'
import DeckCardList from '../components/DeckCardList'
import SelectActionBar from '../components/SelectActionBar'

export default function DeckDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const targetCardId = searchParams.get('cardId')
  const [deck, setDeck] = useState(null)
  const [allCards, setAllCards] = useState([])
  const [keyword, setKeyword] = useState('')
  const [filter, setFilter] = useState(null)
  const [sortOrder, setSortOrder] = useState(() => getDeckCardSortOrder(id))
  const [adding, setAdding] = useState(false)
  const [newFace, setNewFace] = useState({ a: EMPTY_FACE, b: EMPTY_FACE })
  const [createError, setCreateError] = useState('')
  const [editing, setEditing] = useState(null)
  const [editError, setEditError] = useState('')
  const [deletingId, setDeletingId] = useState(null)
  const [resettingId, setResettingId] = useState(null)
  const [csvOpen, setCsvOpen] = useState(false)
  const [csvText, setCsvText] = useState('')
  const [csvResult, setCsvResult] = useState(null)
  const [moveOpen, setMoveOpen] = useState(false)
  const [moveTargetDeckId, setMoveTargetDeckId] = useState('')
  const [moveDecks, setMoveDecks] = useState([])
  const [moveResult, setMoveResult] = useState(null)
  const [moveError, setMoveError] = useState('')
  const [movingCards, setMovingCards] = useState(false)
  const [stats, setStats] = useState(EMPTY_STATS)
  const [totalCount, setTotalCount] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  // 末尾窗口跳转：为 true 时底部锚定在卡片末尾（点下箭头跳来的），前向 sentinel 关闭。
  // 此时仍可向上滑动逆向加载，把中间卡片一页页补回来，避免大卡包一次性塞满 DOM。
  const [tailMode, setTailMode] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadingPrev, setLoadingPrev] = useState(false)
  // 当前已加载窗口对应的全局起始偏移：allCards[0] 在整份卡片里的下标。
  // 首页窗口=0；末尾窗口=total-PAGE_SIZE；向上逆向加载时递减，到 0 即接回开头。
  const windowStartRef = useRef(0)
  const [hasPrev, setHasPrev] = useState(false)
  const prevLoadingRef = useRef(false)
  const tailJumpRef = useRef(false)
  // 末尾窗口换回首页期间，阻止底部 sentinel 在回顶前误追加第二页。
  const headJumpRef = useRef(false)
  const [scrollToBottomPending, setScrollToBottomPending] = useState(false)
  const [scrollToTopPending, setScrollToTopPending] = useState(false)
  const [todayCards, setTodayCards] = useState(null)
  const [todayLoading, setTodayLoading] = useState(false)
  const [todayInvalidateCount, setTodayInvalidateCount] = useState(0)
  const [tomorrowCards, setTomorrowCards] = useState(null)
  const [tomorrowLoading, setTomorrowLoading] = useState(false)
  const [tomorrowInvalidateCount, setTomorrowInvalidateCount] = useState(0)
  const [newCardsPerDay, setNewCardsPerDay] = useState(10)
  const [highlightedCardId, setHighlightedCardId] = useState(null)
  const loadTokenRef = useRef(0)
  const deferredCardsTokenRef = useRef(0)
  const sentinelRef = useRef(null)
  const loadNextPageRef = useRef(null)
  const loadPrevPageRef = useRef(null)
  const topSentinelRef = useRef(null)
  const searchDebounceRef = useRef(null)
  const loadingMoreRequestRef = useRef(0)
  const scrollToBottomLoadingRef = useRef(false)
  const targetCardLoadingRef = useRef(false)
  const highlightTimerRef = useRef(null)
  const [isLoadingAll, setIsLoadingAll] = useState(false)
  const [selectMenuOpen, setSelectMenuOpen] = useState(false)
  const [batchDeleteConfirm, setBatchDeleteConfirm] = useState(false)
  const [batchResetConfirm, setBatchResetConfirm] = useState(false)
  const [dayRolloverNoticeOpen, setDayRolloverNoticeOpen] = useState(
    () => Boolean(location.state?.practiceDayRolledOver)
  )
  const [headerCollapsed, setHeaderCollapsed] = useState(false)
  const [headerDetached, setHeaderDetached] = useState(false)
  const collapsibleHeaderRef = useRef(null)
  const previousScrollYRef = useRef(0)
  const { sideScrollHandlers, renderSideHints } = useSideScrollHints({
    onTop: scrollRootToTop,
    onBottom: autoLoadToBottom,
  })

  useEffect(() => {
    if (!location.state?.practiceDayRolledOver) return
    navigate(`${location.pathname}${location.search}`, { replace: true, state: null })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const root = document.getElementById('root')
    if (!root) return
    previousScrollYRef.current = root.scrollTop
    function onScroll() {
      const previousScrollY = previousScrollYRef.current
      const nextScrollY = root.scrollTop
      if (nextScrollY <= 0) {
        setHeaderCollapsed(false)
        setHeaderDetached(false)
      } else if (shouldAutoCollapseDeckHeader({
        previousScrollTop: previousScrollY,
        nextScrollTop: nextScrollY,
        scrollHeight: root.scrollHeight,
        clientHeight: root.clientHeight,
        collapsibleHeight: collapsibleHeaderRef.current?.scrollHeight ?? 0,
      })) {
        setHeaderCollapsed(true)
        setHeaderDetached(true)
      }
      previousScrollYRef.current = nextScrollY
    }
    root.addEventListener('scroll', onScroll, { passive: true })
    return () => root.removeEventListener('scroll', onScroll)
  }, [])

  // 首页 DOM 提交后先瞬间回顶，再让普通分页的 IntersectionObserver 开始工作。
  useLayoutEffect(() => {
    if (!scrollToTopPending) return
    const root = document.getElementById('root')
    root?.scrollTo({ top: 0, behavior: 'auto' })
    setHeaderCollapsed(false)
    setHeaderDetached(false)
    headJumpRef.current = false
    setScrollToTopPending(false)
  }, [scrollToTopPending])

  function closeDayRolloverNotice() {
    setDayRolloverNoticeOpen(false)
  }

  /**
   * 统一判断异步结果是不是旧请求，防止补载串台。
   */
  function isStaleLoad(token) {
    return loadTokenRef.current !== token
  }

  /**
   * 判断今天/明天卡片请求是不是旧请求。
   */
  function isStaleDeferredLoad(token, deferredToken) {
    return isStaleLoad(token) || deferredCardsTokenRef.current !== deferredToken
  }

  function resetDeferredFilterCards() {
    deferredCardsTokenRef.current += 1
    setTodayCards(null)
    setTodayLoading(false)
    setTomorrowCards(null)
    setTomorrowLoading(false)
    setTodayInvalidateCount(c => c + 1)
    setTomorrowInvalidateCount(c => c + 1)
  }

  /**
   * 只让排序设置影响全部、新卡、正在背列表。
   */
  function sortForCardListFilter(nextFilter, nextSort) {
    if (!nextFilter || nextFilter === 'new' || nextFilter === 'learning') return nextSort
    return null
  }

  /**
   * 后台清理继续记录；清理失败不挡用户已经完成的删除或重置。
   */
  function prunePracticeSessionInBackground(cardId) {
    void prunePracticeSessionForRemovedCard(id, cardId).catch(() => {})
  }

  /**
   * 批量删除后后台一次性清理继续记录。
   */
  function prunePracticeSessionsInBackground(cardIds) {
    if (cardIds.length === 0) return
    void prunePracticeSessionForRemovedCards(id, cardIds).catch(() => {})
  }

  /**
   * 单独拉顶部统计，让首屏列表先出来。
   */
  async function loadDeckStats(token, dailyNewLimit) {
    try {
      const nextStats = await getDeckCardStats(id, dailyNewLimit)
      if (isStaleLoad(token)) return
      setStats({
        total: nextStats?.total ?? 0,
        new: nextStats?.newCount ?? 0,
        learning: nextStats?.learningCount ?? 0,
        tomorrow: nextStats?.tomorrowCount ?? 0,
        today: nextStats?.todayCount ?? 0,
        backlog: nextStats?.backlogCount ?? 0,
        newPaused: Boolean(nextStats?.newCardsPaused),
      })
    } catch {
      if (isStaleLoad(token)) return
      setStats({ total: totalCount, new: 0, learning: 0, tomorrow: 0, today: 0, backlog: 0, newPaused: false })
    }
  }

  /**
   * 拉取指定偏移量的一页卡片。默认追加到列表末尾；replace=true 时整份替换列表
   * （用于末尾窗口跳转/回首页窗口，把 DOM 收缩回一页）。
   * 返回 page 对象，若请求过期则返回 null。
   */
  async function fetchPage(offset, { replace = false } = {}) {
    const token = loadTokenRef.current
    const requestId = loadingMoreRequestRef.current + 1
    loadingMoreRequestRef.current = requestId
    setLoadingMore(true)
    try {
      const page = await getCardsPageByDeck(id, {
        offset,
        limit: PAGE_SIZE,
        keyword,
        state: toApiState(filter),
        sort: sortForCardListFilter(filter, sortOrder),
      })
      if (isStaleLoad(token)) return null
      setAllCards(prev => replace ? (page?.items ?? []) : [...prev, ...(page?.items ?? [])])
      setHasMore(page?.hasMore ?? false)
      return page
    } finally {
      if (loadingMoreRequestRef.current === requestId) setLoadingMore(false)
    }
  }

  /**
   * 记录当前窗口起始偏移，并据此点亮/熄灭顶部逆向加载 sentinel。
   * 用 ref 存偏移供 await 后的计算读取（不踩 React 闭包陈旧值），用 state 驱动渲染。
   */
  function setWindowStart(offset) {
    windowStartRef.current = offset
    setHasPrev(offset > 0)
  }

  // 向下滑：前向续拉下一页（仅首页/普通窗口，末尾窗口 hasMore=false 不走这里）。
  async function loadNextPage() {
    if (headJumpRef.current || loadingMore || prevLoadingRef.current || !hasMore) return
    await fetchPage(allCards.length)
  }
  loadNextPageRef.current = loadNextPage

  /**
   * 向上滑：逆向加载窗口前一页并 prepend，同时补偿 scrollTop 让手指位置不跳。
   * 末尾窗口里把中间卡片一页页补回来用；到 offset=0 即接回开头，hasPrev 关闭。
   */
  async function loadPrevPage() {
    if (loadingMore || prevLoadingRef.current || tailJumpRef.current) return
    const start = windowStartRef.current
    if (start <= 0) return
    const nextStart = Math.max(0, start - PAGE_SIZE)
    const limit = start - nextStart
    const root = document.getElementById('root')
    const prevHeight = root?.scrollHeight ?? 0
    const prevTop = root?.scrollTop ?? 0
    const token = loadTokenRef.current
    prevLoadingRef.current = true
    setLoadingPrev(true)
    try {
      const page = await getCardsPageByDeck(id, {
        offset: nextStart,
        limit,
        keyword,
        state: toApiState(filter),
        sort: sortForCardListFilter(filter, sortOrder),
      })
      if (isStaleLoad(token)) return
      const items = page?.items ?? []
      setAllCards(prev => [...items, ...prev])
      setWindowStart(nextStart)
      // 新内容插在上方会把视口顶下去，等下一帧 DOM 撑高后把多出来的高度补回 scrollTop。
      requestAnimationFrame(() => {
        if (!root) return
        root.scrollTop = prevTop + (root.scrollHeight - prevHeight)
      })
    } finally {
      prevLoadingRef.current = false
      setLoadingPrev(false)
    }
  }
  loadPrevPageRef.current = loadPrevPage

  /**
   * 一次性补完剩余分页，并把每页新增卡片交给调用方收集。
   * 循环只看每页返回的 page.hasMore（而非组件 state），故可在 await 之后安全调用，
   * 不受 React 闭包陈旧 hasMore/allCards 影响。
   * @param onItems 每页新增卡片回调
   * @param startOffset 起始偏移；缺省从当前已加载长度续拉
   * @param replaceFirst 首页是否整份替换列表（末尾窗口先回首页时用）
   * 返回明确状态，调用方据此决定是否滚到底，避免旧请求或网络失败时误跳转。
   */
  async function loadAllRemainingPages(onItems, { startOffset, replaceFirst = false } = {}) {
    if (scrollToBottomLoadingRef.current || loadingMore) return LOAD_ALL_STATUS.BUSY
    scrollToBottomLoadingRef.current = true
    try {
      let offset = startOffset ?? allCards.length
      let first = true
      while (true) {
        // fetchPage 内部已有 isStaleLoad(token) 检查：若 loadTokenRef 变化（用户切换筛选）
        // fetchPage 返回 null，下一行的 `if (!page) return` 安全终止循环。
        const page = await fetchPage(offset, { replace: replaceFirst && first })
        first = false
        if (!page) return LOAD_ALL_STATUS.STALE_OR_ERROR
        onItems?.(page.items ?? [])
        if (!page.hasMore) return LOAD_ALL_STATUS.LOADED
        // 用本页实际条数步进，始终指向下一批数据的起始位置。
        offset += page.items.length
      }
    } catch {
      // 网络异常等，静默忽略
      return LOAD_ALL_STATUS.STALE_OR_ERROR
    } finally {
      scrollToBottomLoadingRef.current = false
    }
  }

  // 右侧向下双箭头准备到底时，先把顶部操作区移出页面流，再让底部定位 effect 读取最终高度。
  function queueScrollToBottom() {
    setHeaderCollapsed(true)
    setHeaderDetached(true)
    setScrollToBottomPending(true)
  }

  /**
   * 右侧向下双箭头：跳到卡片末尾。只取末尾一页（offset=total-PAGE_SIZE）替换列表，
   * 中间卡片不加载，DOM 恒定一页，避免大卡包全量加载拖垮主线程。
   */
  async function autoLoadToBottom() {
    if (scrollToBottomLoadingRef.current || loadingMore) return
    // 延迟筛选（今天/明天）整组已在内存，或已在末尾窗口：无需拉数据，直接滚到底
    if (isDeferredFilter(filter) || tailMode) {
      queueScrollToBottom()
      return
    }
    const tailOffset = Math.max(0, totalCount - PAGE_SIZE)
    // 不足一页，首页即全部，直接滚到底
    if (tailOffset === 0) {
      queueScrollToBottom()
      return
    }
    // 标记跳转中：瞬间滚到底前先压住顶部逆向 sentinel，避免替换瞬间它在视口里误触发。
    tailJumpRef.current = true
    const page = await fetchPage(tailOffset, { replace: true })
    if (!page) {
      tailJumpRef.current = false
      return
    }
    // fetchPage 已据末尾页 page.hasMore=false 关掉前向 sentinel；记窗口起点点亮逆向 sentinel
    setTailMode(true)
    setWindowStart(tailOffset)
    queueScrollToBottom()
  }

  /**
   * 右侧向上双箭头：回到页面顶部。若处于末尾窗口，先把列表换回首页一页并恢复前向
   * sentinel，再滚到顶；否则直接滚到顶。
   */
  function scrollRootToTop() {
    if (tailMode) {
      void exitTailToHead()
      return
    }
    document.getElementById('root')?.scrollTo({ top: 0, behavior: 'smooth' })
  }

  /**
   * 从末尾窗口退回首页窗口：重拉首页整份替换列表，fetchPage 据 page.hasMore 复活
   * 前向 sentinel，恢复正常的从头无限滚动。
   */
  async function exitTailToHead() {
    if (headJumpRef.current) return
    headJumpRef.current = true
    let topScrollQueued = false
    try {
      const page = await fetchPage(0, { replace: true })
      if (!page) return
      setTailMode(false)
      setWindowStart(0)
      setScrollToTopPending(true)
      topScrollQueued = true
    } finally {
      if (!topScrollQueued) headJumpRef.current = false
    }
  }

  /**
   * 切换筛选条件或搜索词时，重置列表并重新拉第一页。
   */
  async function reloadCardsWithFilter(nextFilter, nextKeyword, nextSort = sortOrder) {
    const token = loadTokenRef.current + 1
    loadTokenRef.current = token
    setIsLoadingAll(false)
    setAllCards([])
    setHasMore(false)
    setTailMode(false)
    setWindowStart(0)
    resetDeferredFilterCards()
    void loadDeckStats(token, newCardsPerDay)
    if (isDeferredFilter(nextFilter)) return
    const page = await getCardsPageByDeck(id, {
      offset: 0,
      limit: PAGE_SIZE,
      keyword: nextKeyword,
      state: nextFilter,
      sort: sortForCardListFilter(nextFilter, nextSort),
    })
    if (isStaleLoad(token)) return
    setAllCards(page?.items ?? [])
    setHasMore(page?.hasMore ?? false)
    setTotalCount(page?.total ?? 0)
  }

  /**
   * 卡包详情页首屏先拿卡包信息和前 50 条卡片。
   */
  async function load() {
    const token = loadTokenRef.current + 1
    loadTokenRef.current = token

    setIsLoadingAll(false)
    resetDeferredFilterCards()
    setStats(EMPTY_STATS)
    setAllCards([])
    setHasMore(false)
    setTailMode(false)
    setWindowStart(0)

    const nextSortOrder = getDeckCardSortOrder(id)
    setSortOrder(nextSortOrder)

    const [nextDeck, firstPage, deckSettings] = await Promise.all([
      getDeck(id),
      getCardsPageByDeck(id, {
        offset: 0,
        limit: PAGE_SIZE,
        keyword,
        state: toApiState(filter),
        sort: sortForCardListFilter(filter, nextSortOrder),
      }),
      getDeckSettings(id),
    ])
    if (isStaleLoad(token)) return

    const firstBatch = Array.isArray(firstPage?.items) ? firstPage.items : []

    setDeck(nextDeck)
    setAllCards(firstBatch)
    setHasMore(firstPage?.hasMore ?? false)
    setTotalCount(firstPage?.total ?? firstBatch.length)
    setNewCardsPerDay(deckSettings.newCardsPerDay)

    void loadDeckStats(token, deckSettings.newCardsPerDay)
  }

  /**
   * “今天”筛选只在真的点开时再单独查。
   */
  async function loadTodayCardsForFilter() {
    if (todayCards !== null) return todayCards
    const token = loadTokenRef.current
    const deferredToken = deferredCardsTokenRef.current
    setTodayLoading(true)
    try {
      const cards = await getTodayCardsByDeck(id, newCardsPerDay)
      if (isStaleDeferredLoad(token, deferredToken)) return null
      setTodayCards(cards)
      return cards
    }
    finally {
      if (!isStaleDeferredLoad(token, deferredToken)) {
        setTodayLoading(false)
      }
    }
  }

  async function ensureTodayCardsLoaded() {
    await loadTodayCardsForFilter()
  }

  /**
   * “明天复习”筛选只在点开时再单独查。
   */
  async function loadTomorrowCardsForFilter() {
    if (tomorrowCards !== null) return tomorrowCards
    const token = loadTokenRef.current
    const deferredToken = deferredCardsTokenRef.current
    setTomorrowLoading(true)
    try {
      const cards = await getTomorrowCardsByDeck(id)
      if (isStaleDeferredLoad(token, deferredToken)) return null
      setTomorrowCards(cards)
      setStats(prev => ({ ...prev, tomorrow: cards.length }))
      return cards
    } finally {
      if (!isStaleDeferredLoad(token, deferredToken)) {
        setTomorrowLoading(false)
      }
    }
  }

  async function ensureTomorrowCardsLoaded() {
    await loadTomorrowCardsForFilter()
  }

  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    void load()
    return () => {
      loadTokenRef.current += 1
    }
  }, [id])

  useEffect(() => {
    if (filter !== 'today') return
    void ensureTodayCardsLoaded()
  }, [filter, id, newCardsPerDay, todayInvalidateCount])

  useEffect(() => {
    if (filter !== 'tomorrow') return
    void ensureTomorrowCardsLoaded()
  }, [filter, id, tomorrowInvalidateCount])

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasMore) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadNextPageRef.current?.()
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore])

  // 顶部 sentinel：仅当窗口起点>0（hasPrev）时挂；滑到顶就逆向加载前一页。
  useEffect(() => {
    const sentinel = topSentinelRef.current
    if (!sentinel || !hasPrev) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadPrevPageRef.current?.()
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasPrev])

  useEffect(() => {
    if (scrollToBottomPending && !loadingMore && !hasMore) {
      setScrollToBottomPending(false)
      const root = document.getElementById('root')
      // 末尾窗口跳转用瞬间滚动：滚完 scrollTop 立刻到底，顶部 sentinel 随即离开视口，
      // 再松开 tailJumpRef，杜绝替换瞬间的逆向误触发。其余场景仍平滑滚动。
      const jumping = tailJumpRef.current
      if (jumping) {
        // 末尾卡片含 markdown/图片，挂载后高度会继续撑大；只滚一次会停在「真底部上面一点」。
        // 连续多帧重钉到底，等高度不再变化（或到上限帧数）才松开 tailJumpRef，确保贴住真底部。
        let stableCount = 0
        let frames = 0
        let lastHeight = -1
        const pin = () => {
          root.scrollTo({ top: root.scrollHeight, behavior: 'auto' })
          if (root.scrollHeight === lastHeight) stableCount += 1
          else stableCount = 0
          lastHeight = root.scrollHeight
          frames += 1
          // 高度连续 3 帧不变即稳定；最多 40 帧（约 0.6s）兜底，防异步内容永不停撑大时死循环。
          if (stableCount >= 3 || frames >= 40) {
            tailJumpRef.current = false
            return
          }
          requestAnimationFrame(pin)
        }
        requestAnimationFrame(pin)
      } else {
        root.scrollTo({ top: root.scrollHeight, behavior: 'smooth' })
      }
    }
  }, [scrollToBottomPending, loadingMore, hasMore])

  useEffect(() => {
    if (!targetCardId) return
    if (keyword || filter) {
      setKeyword('')
      setFilter(null)
      void reloadCardsWithFilter(null, '')
      return
    }

    const targetElement = Array.from(document.querySelectorAll('[data-card-id]'))
      .find((element) => element.dataset.cardId === targetCardId)
    if (targetElement) {
      scrollElementIntoRootCenter(document.getElementById('root'), targetElement)
      setHighlightedCardId(targetCardId)
      if (highlightTimerRef.current) {
        clearTimeout(highlightTimerRef.current)
      }
      highlightTimerRef.current = setTimeout(() => {
        setHighlightedCardId((current) => (current === targetCardId ? null : current))
      }, 2400)
      return
    }

    if (!hasMore || loadingMore || targetCardLoadingRef.current) return
    targetCardLoadingRef.current = true
    fetchPage(allCards.length).finally(() => {
      targetCardLoadingRef.current = false
    })
  }, [targetCardId, allCards, hasMore, loadingMore, keyword, filter])

  useEffect(() => () => {
    if (highlightTimerRef.current) {
      clearTimeout(highlightTimerRef.current)
    }
  }, [])

  /* eslint-enable react-hooks/exhaustive-deps */

  /**
   * 页面空白点击时关闭多选菜单。
   */
  function handlePageClick() {
    if (selectMenuOpen) setSelectMenuOpen(false)
  }

  /**
   * 打开批量删除确认弹窗，未选中时不做任何事。
   */
  function handleBatchDelete() {
    if (selectedIds.length === 0) return
    setBatchDeleteConfirm(true)
  }

  /**
   * 确认后删除选中卡片，并同步刷新列表、数量和顶部统计。
   */
  async function confirmBatchDelete() {
    const idsToDelete = [...selectedIds]
    setBatchDeleteConfirm(false)
    exitSelectMode()

    const results = await Promise.allSettled(idsToDelete.map(cardId => deleteCard(cardId)))
    const deletedIds = idsToDelete.filter((_, index) => results[index].status === 'fulfilled')
    if (deletedIds.length === 0) return

    const deletedIdSet = new Set(deletedIds.map(String))
    setAllCards(cards => cards.filter(c => !deletedIdSet.has(String(c.id))))
    setTodayCards(cards => cards === null ? cards : cards.filter(c => !deletedIdSet.has(String(c.id))))
    setTomorrowCards(cards => cards === null ? cards : cards.filter(c => !deletedIdSet.has(String(c.id))))
    setTotalCount(n => Math.max(0, n - deletedIds.length))
    void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    prunePracticeSessionsInBackground(deletedIds)
  }

  /**
   * 打开批量重置确认弹窗，未选中时不做任何事。
   */
  function handleBatchReset() {
    if (selectedIds.length === 0) return
    setBatchResetConfirm(true)
  }

  /**
   * 确认后重置选中卡片，并把成功返回的卡片同步成新卡状态。
   */
  async function confirmBatchReset() {
    const idsToReset = [...selectedIds]
    setBatchResetConfirm(false)
    exitSelectMode()

    const results = await Promise.allSettled(idsToReset.map(cardId => resetCard(cardId)))
    const successIds = []
    results.forEach((result, index) => {
      if (result.status === 'fulfilled' && result.value) {
        const nextCard = result.value
        successIds.push(idsToReset[index])
        setAllCards(cards => replaceCardInLoadedList(cards, nextCard))
        setTodayCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, nextCard))
        setTomorrowCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, nextCard))
      }
    })
    void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    if (successIds.length > 0) prunePracticeSessionsInBackground(successIds)
  }

  /**
   * 打开批量迁移弹窗，并加载除当前卡包外的目标卡包列表。
   */
  async function handleBatchMove() {
    if (selectedIds.length === 0) return
    setMoveError('')
    setMoveResult(null)
    setMoveTargetDeckId('')
    setMoveDecks([])
    setMoveOpen(true)
    try {
      const decks = await getAllDecks()
      setMoveDecks((decks ?? []).filter(deck => String(deck.id) !== String(id)))
    } catch (error) {
      setMoveError(getErrorMessage(error?.code))
    }
  }

  /**
   * 确认迁移后按后端返回的 movedCardIds 从当前卡包界面移除成功项。
   */
  async function confirmBatchMove() {
    if (!moveTargetDeckId || selectedIds.length === 0 || movingCards) return
    setMovingCards(true)
    setMoveError('')
    try {
      const result = await moveCardsBatch(id, Number(moveTargetDeckId), selectedIds.map(Number))
      setMoveResult(result)
      const movedIdSet = new Set((result?.movedCardIds ?? []).map(String))
      if (movedIdSet.size > 0) {
        setAllCards(cards => cards.filter(c => !movedIdSet.has(String(c.id))))
        setTodayCards(cards => cards === null ? cards : cards.filter(c => !movedIdSet.has(String(c.id))))
        setTomorrowCards(cards => cards === null ? cards : cards.filter(c => !movedIdSet.has(String(c.id))))
        setTotalCount(n => Math.max(0, n - movedIdSet.size))
        const nextSelectedIds = selectedIds.filter(cardId => !movedIdSet.has(String(cardId)))
        if (nextSelectedIds.length === 0) {
          exitSelectMode()
        } else {
          setSelectedIds(nextSelectedIds)
        }
        void loadDeckStats(loadTokenRef.current, newCardsPerDay)
        prunePracticeSessionsInBackground([...movedIdSet])
      }
    } catch (error) {
      setMoveError(getErrorMessage(error?.code))
    } finally {
      setMovingCards(false)
    }
  }

  /**
   * 关闭批量迁移弹窗并清理临时状态。
   */
  function closeMoveModal() {
    setMoveOpen(false)
    setMoveTargetDeckId('')
    setMoveResult(null)
    setMoveError('')
  }

  /**
   * 全选所有已匹配卡片；未加载完时先补齐分页，再一次性同步选中项。
   */
  async function handleSelectAllLoad() {
    setSelectMenuOpen(false)
    if (isDeferredFilter(filter)) {
      const token = loadTokenRef.current
      setIsLoadingAll(true)
      try {
        const deferredCards = await loadDeferredFilterCardsForSelectAll()
        if (loadTokenRef.current === token && deferredCards !== null) {
          setSelectedIds(filterCardsByKeyword(deferredCards, keyword).map(c => String(c.id)))
        }
      } finally {
        if (loadTokenRef.current === token) setIsLoadingAll(false)
      }
      return
    }

    // 真正"全部已加载"才能直接全选当前列表：末尾窗口虽 hasMore=false，但中间未加载，需补齐。
    if (!hasMore && !tailMode) {
      setSelectedIds(displayedCards.map(c => String(c.id)))
      return
    }

    setIsLoadingAll(true)
    const token = loadTokenRef.current
    const seenIds = new Set()
    const collectedIds = []
    const collect = (items) => items.forEach(c => {
      const sid = String(c.id)
      if (!seenIds.has(sid)) {
        seenIds.add(sid)
        collectedIds.push(sid)
      }
    })

    try {
      // 末尾窗口：从首页起整份重拉（首页替换列表），回到从头累加模型再补完；
      // 普通模式：先收已加载部分，再续拉剩余分页。
      let result
      if (tailMode) {
        result = await loadAllRemainingPages(collect, { startOffset: 0, replaceFirst: true })
        setTailMode(false)
        setWindowStart(0)
      } else {
        collect(allCards)
        result = await loadAllRemainingPages(collect)
      }

      if (
        loadTokenRef.current === token &&
        result !== LOAD_ALL_STATUS.BUSY &&
        result !== LOAD_ALL_STATUS.STALE_OR_ERROR
      ) {
        setSelectedIds(collectedIds)
      }
    } finally {
      if (loadTokenRef.current === token) setIsLoadingAll(false)
    }
  }

  async function loadDeferredFilterCardsForSelectAll() {
    if (filter === 'today') return loadTodayCardsForFilter()
    if (filter === 'tomorrow') return loadTomorrowCardsForFilter()
    return null
  }

  /**
   * 当前已显示卡片全选。
   */
  function handleSelectAllCurrent() {
    setSelectedIds(displayedCards.map(c => String(c.id)))
    setSelectMenuOpen(false)
  }

  /**
   * 搜索卡片关键字，300ms 防抖后重新从后端查询。
   */
  function handleSearch(kw) {
    setKeyword(kw)
    clearTimeout(searchDebounceRef.current)
    searchDebounceRef.current = setTimeout(() => {
      void reloadCardsWithFilter(filter, kw)
    }, 300)
  }

  /**
   * 切换筛选条件。
   */
  function handleFilterChange(key) {
    const nextFilter = filter === key ? null : key
    setFilter(nextFilter)
    void reloadCardsWithFilter(nextFilter, keyword)
  }

  // 非 today/tomorrow 筛选由后端过滤，today/tomorrow 卡片是前端单独集合再做关键字过滤
  const baseCards = filter === 'today'
    ? (todayCards ?? [])
    : filter === 'tomorrow'
      ? (tomorrowCards ?? [])
      : allCards
  const displayedCards = isDeferredFilter(filter)
    ? filterCardsByKeyword(baseCards, keyword)
    : baseCards
  const moveTargetDecks = moveDecks.filter(deck => String(deck.id) !== String(id))

  if (filter === 'today' || filter === 'tomorrow') {
    const today = getToday()
    displayedCards.sort((a, b) => compareTodayCards(a, b, today))
  }

  const {
    isSelectMode, selectedIds, setSelectedIds,
    exitSelectMode, handleToggleSelect,
    handlePointerDown, handlePointerMove, handlePointerUp,
    handlePointerCancel, handlePointerLeave,
  } = useDragSelect({ items: displayedCards, getId: c => c.id, dataAttr: 'data-card-id' })
  const pointerHandlers = {
    onPointerDown: handlePointerDown,
    onPointerMove: handlePointerMove,
    onPointerUp: handlePointerUp,
    onPointerLeave: handlePointerLeave,
    onPointerCancel: handlePointerCancel,
  }

  /**
   * 判断新建卡片是否能安全放到当前列表第一位。
   */
  function canPrependCreatedCard(card) {
    if (sortOrder !== 'created_desc') return false
    if (tailMode) return false
    if (windowStartRef.current !== 0) return false
    if (filter && filter !== 'new') return false
    const kw = keyword.trim().toLowerCase()
    if (!kw) return true
    return (card.sideA ?? '').toLowerCase().includes(kw) || (card.sideB ?? '').toLowerCase().includes(kw)
  }

  /**
   * 新增一张卡片后按当前排序同步列表。
   */
  async function handleCreate() {
    const { a, b } = newFace
    if ((!a.text && a.images.length === 0) && (!b.text && b.images.length === 0)) {
      setCreateError(t('deckDetail.atLeastOneSide'))
      return
    }
    setCreateError('')
    try {
      const newCard = await createCard(id, a.text, b.text, a.images, b.images)
      setNewFace({ a: EMPTY_FACE, b: EMPTY_FACE })
      setAdding(false)
      if (newCard) {
        if (canPrependCreatedCard(newCard)) {
          setAllCards(cards => [newCard, ...cards])
        } else {
          if (isDeferredFilter(filter)) {
            resetDeferredFilterCards()
          }
          void silentRefreshCards()
        }
        setTotalCount(n => n + 1)
      }
      void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    } catch (error) {
      setCreateError(getErrorMessage(error?.code))
    }
  }

  /**
   * 保存卡片编辑后用返回值原地替换列表中那张卡，不整页刷新。
   */
  async function handleUpdate() {
    const { a, b } = editing
    if ((!a.text && a.images.length === 0) && (!b.text && b.images.length === 0)) {
      setEditError(t('deckDetail.atLeastOneSide'))
      return
    }
    setEditError('')
    try {
      const updatedCard = await updateCard(editing.id, a.text, b.text, a.images, b.images)
      setEditing(null)
      if (updatedCard) {
        setAllCards(cards => replaceCardInLoadedList(cards, updatedCard))
        setTodayCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, updatedCard))
        setTomorrowCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, updatedCard))
      }
      void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    } catch (error) {
      setEditError(getErrorMessage(error?.code))
    }
  }

  /**
   * 删除卡片后本地过滤掉那张卡，不整页刷新。
   */
  async function handleDelete() {
    const idToDelete = deletingId
    await deleteCard(idToDelete)
    setDeletingId(null)
    setAllCards(cards => cards.filter(c => String(c.id) !== String(idToDelete)))
    setTodayCards(cards => cards === null ? cards : cards.filter(c => String(c.id) !== String(idToDelete)))
    setTomorrowCards(cards => cards === null ? cards : cards.filter(c => String(c.id) !== String(idToDelete)))
    setTotalCount(n => Math.max(0, n - 1))
    void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    prunePracticeSessionInBackground(idToDelete)
  }

  /**
   * 重置卡片进度后只替换已加载列表中的这张卡片。
   */
  async function handleReset(cardId) {
    const nextCard = await resetCard(cardId)
    setResettingId(null)
    setAllCards(cards => replaceCardInLoadedList(cards, nextCard))
    setTodayCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, nextCard))
    setTomorrowCards(cards => cards === null ? cards : replaceCardInLoadedList(cards, nextCard))
    void loadDeckStats(loadTokenRef.current, newCardsPerDay)
    prunePracticeSessionInBackground(cardId)
  }

  /**
   * 静默刷新卡片列表：等数据回来再 swap，不清空列表，不闪屏。
   * 适用于批量操作后后端新增/删除了多张卡片，本地无法直接推算结果的场景。
   */
  async function silentRefreshCards() {
    const token = loadTokenRef.current
    const page = await getCardsPageByDeck(id, {
      offset: 0,
      limit: PAGE_SIZE,
      keyword,
      state: toApiState(filter),
      sort: sortForCardListFilter(filter, sortOrder),
    })
    if (token !== loadTokenRef.current) return
    setAllCards(page?.items ?? [])
    setHasMore(page?.hasMore ?? false)
    setTailMode(false)
    setWindowStart(0)
    setTotalCount(page?.total ?? 0)
    void loadDeckStats(token, newCardsPerDay)
  }

  /**
   * 批量导入完成后静默刷新卡片列表，不整页刷新。
   */
  async function handleCsvImport() {
    const { cards, invalidCount, failures } = parseCardTextRows(csvText, t)
    const result = await createCardsBatch(id, cards)
    setCsvResult({
      createdCount: result?.createdCount ?? 0,
      duplicateCount: result?.duplicateCount ?? 0,
      invalidCount: invalidCount + (result?.invalidCount ?? 0),
      failures: [...failures, ...(result?.failures ?? [])],
    })
    setCsvText('')
    void silentRefreshCards()
  }

  /**
   * 关闭批量导入弹窗并清掉临时输入。
   */
  function handleCsvClose() {
    setCsvOpen(false)
    setCsvText('')
    setCsvResult(null)
  }

  /**
   * 卡包信息未返回前只显示加载提示。
   */
  if (!deck) {
    return (
      <div className="flex h-full items-center justify-center gap-3 text-sm text-app-label-secondary">
        <Preloader />
        {t('deckDetail.loading')}
      </div>
    )
  }

  return (
    <div
      onClick={handlePageClick}
    >
      <AppPage
        bottomInset={isSelectMode ? 'selection' : 'none'}
        contentClassName="!pt-0"
        style={{ touchAction: 'manipulation' }}
        {...sideScrollHandlers}
      >
        <section
          className="sticky top-0 z-30 mb-8 bg-app-background shadow-[0_10px_18px_-18px_var(--app-elevation-shadow)]"
          style={{ overflowAnchor: 'none' }}
        >
          <AppNavbar
            className={`!static transition-[margin] duration-300 ease-out motion-reduce:transition-none ${headerCollapsed ? 'mb-0' : 'mb-2'}`}
            innerClassName="!pl-0 !pr-0"
            title={<h1 className="max-w-[52vw] truncate">{deck.name}</h1>}
            left={(
              <NavbarBackLink
                showText
                text={t('common.back').replace(/^←\s*/, '')}
                onClick={withGenericClick(() => navigate('/'))}
              />
            )}
            right={(
              <Button
                inline
                small
                rounded
                clear
                onClick={withGenericClick(() => navigate(`/deck/${id}/practice`))}
                disabled={totalCount === 0}
              >
                {t('deckDetail.startPractice')}
              </Button>
            )}
          />
          <div className={`${headerDetached ? 'absolute inset-x-0 top-full shadow-[0_10px_18px_-18px_var(--app-elevation-shadow)]' : 'relative'} bg-app-background`}>
            <div ref={collapsibleHeaderRef} className={`grid transition-[grid-template-rows,opacity] duration-300 ease-out motion-reduce:transition-none ${headerCollapsed ? 'grid-rows-[0fr] opacity-0' : 'grid-rows-[1fr] opacity-100'}`}>
              <div className="min-h-0 overflow-hidden">
                <div className="mb-4 grid grid-cols-3 gap-2">
                  <Button small tonal rounded onClick={withGenericClick(() => navigate(`/deck/${id}/summary`))}>{t('deckDetail.stats')}</Button>
                  <Button small tonal rounded onClick={withGenericClick(() => { setCsvOpen(true); setCsvResult(null) })}>{t('deckDetail.batchImport')}</Button>
                  <Button small tonal rounded onClick={withGenericClick(() => navigate(`/deck/${id}/settings`))}>{t('deckDetail.settings')}</Button>
                </div>

                {/* 搜索框 */}
                <List
                  inset
                  strong
                  colors={{ strongBgIos: 'bg-app-surface-primary' }}
                  className="!mx-1 !mb-4 !mt-0 ring-1 ring-app-control transition-shadow focus-within:ring-app-focus"
                >
                  <ListInput
                    clearButton={Boolean(keyword)}
                    onClear={() => handleSearch('')}
                    placeholder={t('deckDetail.searchPlaceholder')}
                    value={keyword}
                    onChange={(event) => handleSearch(event.target.value)}
                    inputClassName="text-app-label-primary placeholder:text-app-label-tertiary"
                  />
                </List>

                {/* 状态筛选栏 */}
                <DeckFilterBar stats={stats} filter={filter} onFilterChange={handleFilterChange} />

                {/* 新增卡片表单 */}
                <NewCardForm
                  adding={adding}
                  newFace={newFace}
                  createError={createError}
                  onFaceChange={setNewFace}
                  onCreate={handleCreate}
                  onOpen={() => { setCreateError(''); setAdding(true) }}
                  onCancel={() => { setAdding(false); setCreateError(''); setNewFace({ a: EMPTY_FACE, b: EMPTY_FACE }) }}
                />
              </div>
            </div>

            <button
              type="button"
              aria-expanded={!headerCollapsed}
              aria-label={t(headerCollapsed ? 'deckDetail.expandControls' : 'deckDetail.collapseControls')}
              onClick={withGenericClick(() => setHeaderCollapsed(collapsed => !collapsed))}
              className="group absolute top-full left-1/2 z-10 flex h-12 w-32 -translate-x-1/2 items-center justify-center rounded-b-[2.5rem] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"
            >
              <svg aria-hidden="true" focusable="false" viewBox="0 0 128 48" preserveAspectRatio="none" className="pointer-events-none absolute inset-0 h-full w-full overflow-visible drop-shadow-md">
                <path d="M0 0 C20 0 18 28 48 28 H80 C110 28 108 0 128 0" className="fill-app-background stroke-app-separator" strokeWidth="1" vectorEffect="non-scaling-stroke" />
              </svg>
              <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" className={`relative z-10 h-6 w-6 -translate-y-2 text-app-label-secondary transition-[color,transform] duration-300 group-hover:text-app-accent motion-reduce:transition-none ${headerCollapsed ? '' : 'rotate-180'}`} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="m6 9 6 6 6-6" />
              </svg>
            </button>
          </div>
        </section>

        {/* 卡片列表 */}
        <DeckCardList
          displayedCards={displayedCards}
          filter={filter}
          keyword={keyword}
          adding={adding}
          todayLoading={todayLoading}
          tomorrowLoading={tomorrowLoading}
          highlightedCardId={highlightedCardId}
          isSelectMode={isSelectMode}
          selectedIds={selectedIds}
          deckId={id}
          loadingMore={loadingMore}
          sentinelRef={sentinelRef}
          topSentinelRef={topSentinelRef}
          hasPrev={hasPrev}
          loadingPrev={loadingPrev}
          pointerHandlers={pointerHandlers}
          onEdit={(card) => { setEditError(''); setEditing({ id: card.id, a: { text: card.sideA, images: card.sideAImage ?? [] }, b: { text: card.sideB, images: card.sideBImage ?? [] } }) }}
          onDelete={(cardId) => setDeletingId(cardId)}
          onReset={(cardId) => setResettingId(cardId)}
          onToggleSelect={(cardId) => handleToggleSelect(cardId)}
        />

        {/* 编辑卡片弹窗 */}
        <CardEditModal
          editing={editing}
          editError={editError}
          onChange={setEditing}
          onSave={handleUpdate}
          onCancel={() => { setEditError(''); setEditing(null) }}
        />

        {/* 批量 CSV 导入弹窗 */}
        <CsvImportModal
          open={csvOpen}
          csvText={csvText}
          csvResult={csvResult}
          onTextChange={setCsvText}
          onImport={handleCsvImport}
          onClose={handleCsvClose}
        />

        {/* 批量迁移卡片弹窗 */}
        <CardMoveModal
          open={moveOpen}
          decks={moveTargetDecks}
          selectedTargetDeckId={moveTargetDeckId}
          selectedCount={selectedIds.length}
          moving={movingCards}
          moveResult={moveResult}
          error={moveError}
          onTargetChange={setMoveTargetDeckId}
          onConfirm={confirmBatchMove}
          onClose={closeMoveModal}
        />

        <ConfirmDialog open={!!deletingId} message={t('deckDetail.deleteCardConfirm')} onConfirm={handleDelete} onCancel={() => setDeletingId(null)} />
        <ConfirmDialog
          open={!!resettingId}
          message={t('deckDetail.resetCardConfirm')}
          onConfirm={() => handleReset(resettingId)}
          onCancel={() => setResettingId(null)}
          confirmText={t('deckDetail.confirmReset')}
          confirmButtonClassName="bg-app-warning-fill hover:bg-app-warning-hover active:bg-app-warning-pressed text-app-on-warning disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
        />
        <ConfirmDialog
          open={batchDeleteConfirm}
          message={t('deckDetail.deleteBatchConfirm', { count: selectedIds.length })}
          onConfirm={confirmBatchDelete}
          onCancel={() => setBatchDeleteConfirm(false)}
        />
        <ConfirmDialog
          open={batchResetConfirm}
          message={t('deckDetail.resetBatchConfirm', { count: selectedIds.length })}
          onConfirm={confirmBatchReset}
          onCancel={() => setBatchResetConfirm(false)}
          confirmText={t('deckDetail.confirmReset')}
          confirmButtonClassName="bg-app-warning-fill hover:bg-app-warning-hover active:bg-app-warning-pressed text-app-on-warning disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
        />
    </AppPage>
    {renderSideHints()}
    <SelectActionBar
      isSelectMode={isSelectMode}
      selectedIds={selectedIds}
      selectMenuOpen={selectMenuOpen}
      onToggleMenu={setSelectMenuOpen}
      onSelectAllCurrent={handleSelectAllCurrent}
      onSelectAllLoad={handleSelectAllLoad}
      onBatchMove={handleBatchMove}
      onBatchReset={handleBatchReset}
      onBatchDelete={handleBatchDelete}
      onCancel={exitSelectMode}
    />
    {isLoadingAll && (
      <div
        className="fixed inset-0 z-[60] flex items-center justify-center bg-app-overlay"
        onClick={e => e.stopPropagation()}
        onPointerDown={e => e.stopPropagation()}
        onPointerUp={e => e.stopPropagation()}
      >
        <Preloader />
      </div>
    )}
    <KonstaDialogShell
      open={dayRolloverNoticeOpen}
      onClose={closeDayRolloverNotice}
      ariaLabel={t('practice.dayRolledOver')}
      buttons={<DialogButton strong onClick={withGenericClick(closeDayRolloverNotice)}>{t('common.close')}</DialogButton>}
    >
      <p className="text-base leading-relaxed">{t('practice.dayRolledOver')}</p>
    </KonstaDialogShell>
    </div>
  )
}
