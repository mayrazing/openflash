import { useEffect, useRef, useState } from 'react'
import { lockRootScrollForDragSelect, unlockRootScrollForDragSelect } from '../lib/dragSelectScrollLock'
import { computeRangeSelection } from '../lib/deckCardUtils.js'

/**
 * 从当前手指位置读取下面那个元素的 data-* 属性 ID。
 */
function getIdFromPoint(dataAttr, event) {
  const el = document.elementFromPoint(event.clientX, event.clientY)
  return el?.closest?.(`[${dataAttr}]`)?.getAttribute?.(dataAttr) ?? null
}

/**
 * 通用「长按进入多选 + 再次长按拖拽范围选择 + 吞掉补发 click」手势 hook。
 * 把 DeckDetail / Home 两页逐字重复的指针逻辑收口到一处。
 *
 * @param {Object} cfg
 * @param {Array}  cfg.items     当前显示列表（DeckDetail=displayedCards, Home=decks）
 * @param {Function} [cfg.getId] item => id，默认 it => it.id
 * @param {string} cfg.dataAttr  DOM 定位属性名：'data-card-id' | 'data-deck-id'
 * @param {number} [cfg.longPressMs] 长按阈值，默认 500
 */
export function useDragSelect({ items, getId = it => it.id, dataAttr, longPressMs = 500 }) {
  const [isSelectMode, setIsSelectMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const longPressTimerRef = useRef(null)
  const longPressTriggeredIdRef = useRef(null)
  const longPressStartPosRef = useRef(null)
  const longPressPointerInfoRef = useRef(null)
  const dragSelectRef = useRef(null)
  const dragSelectSuppressClickIdRef = useRef(null)
  const tapSelectedIdsRef = useRef([])

  /**
   * 按当前显示顺序找到项的位置，用来算滑选覆盖区间。
   */
  function getDisplayedIndex(id) {
    return items.findIndex(it => String(getId(it)) === String(id))
  }

  /**
   * 进入多选状态并选中单个项（长按触发）。
   */
  function enterSelectMode(id) {
    setIsSelectMode(true)
    setSelectedIds([String(id)])
    tapSelectedIdsRef.current = [String(id)]
  }

  /**
   * 空集进入多选（Home「导出」按钮）。
   */
  function enterEmptySelectMode() {
    setIsSelectMode(true)
    setSelectedIds([])
    tapSelectedIdsRef.current = []
  }

  /**
   * 退出多选状态，并清掉选中项和长按计时器。
   */
  function exitSelectMode() {
    setIsSelectMode(false)
    setSelectedIds([])
    clearLongPressTimer()
    longPressTriggeredIdRef.current = null
    longPressStartPosRef.current = null
    longPressPointerInfoRef.current = null
    dragSelectRef.current = null
    dragSelectSuppressClickIdRef.current = null
    tapSelectedIdsRef.current = []
    unlockRootScrollForDragSelect()
  }

  /**
   * 切换单个项是否被选中。
   */
  function toggleSelected(id) {
    const sid = String(id)
    setSelectedIds(prev => {
      const removing = prev.includes(sid)
      if (removing) {
        tapSelectedIdsRef.current = tapSelectedIdsRef.current.filter(i => i !== sid)
      } else if (!tapSelectedIdsRef.current.includes(sid)) {
        tapSelectedIdsRef.current = [...tapSelectedIdsRef.current, sid]
      }
      return removing ? prev.filter(i => i !== sid) : [...prev, sid]
    })
  }

  /**
   * 多选状态下点击项；长按触发后的第一下 click 只吞掉，不反选。
   */
  function handleToggleSelect(id) {
    const sid = String(id)
    if (longPressTriggeredIdRef.current === sid) {
      longPressTriggeredIdRef.current = null
      return
    }
    if (dragSelectSuppressClickIdRef.current === sid) {
      dragSelectSuppressClickIdRef.current = null
      return
    }
    toggleSelected(id)
  }

  /**
   * 清掉未触发的长按计时器。
   */
  function clearLongPressTimer() {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current)
      longPressTimerRef.current = null
    }
  }

  /**
   * 开始一次滑选手势，并记录开始前的选中快照。
   */
  function startDragSelect(event, id, snapshotIds = selectedIds, forcedMode = null, preToggled = false) {
    const sid = String(id)
    const anchorIndex = getDisplayedIndex(sid)
    if (anchorIndex === -1) return
    const snapshot = new Set(snapshotIds.map(String))
    lockRootScrollForDragSelect()
    dragSelectRef.current = {
      pointerId: event.pointerId,
      anchorId: sid,
      anchorIndex,
      currentIndex: anchorIndex,
      startX: event.clientX,
      startY: event.clientY,
      mode: forcedMode ?? (snapshot.has(sid) ? 'deselect' : 'select'),
      snapshot,
      started: false,
      preToggledAnchor: preToggled ? sid : null,
    }
    try {
      event.currentTarget.setPointerCapture(event.pointerId)
    } catch {
      // 浏览器不支持或指针已释放时，不影响普通滑选逻辑。
    }
  }

  /**
   * 多选状态下再次长按项后才开始范围滑选，普通上下滑动保留给页面滚动。
   */
  function startDeferredDragSelect(event, id) {
    const sid = String(id)
    longPressStartPosRef.current = { x: event.clientX, y: event.clientY }
    try {
      event.currentTarget.setPointerCapture(event.pointerId)
    } catch {
      // 捕获失败时保留原有点击和滚动行为。
    }
    longPressTimerRef.current = setTimeout(() => {
      longPressTimerRef.current = null
      longPressTriggeredIdRef.current = sid
      const fullCurrent = (selectedIds ?? []).map(String)
      const tapSnapshot = [...tapSelectedIdsRef.current]
      const isCurrentlySelected = fullCurrent.includes(sid)
      const mode = isCurrentlySelected ? 'deselect' : 'select'
      tapSelectedIdsRef.current = isCurrentlySelected
        ? tapSnapshot.filter(i => i !== sid)
        : tapSnapshot.includes(sid) ? tapSnapshot : [...tapSnapshot, sid]
      const postToggle = isCurrentlySelected
        ? fullCurrent.filter(i => i !== sid)
        : [...new Set([...fullCurrent, sid])]
      setSelectedIds(postToggle)
      startDragSelect(event, id, postToggle, mode, true)
    }, longPressMs)
  }

  /**
   * 根据手指当前所在项，重算本次滑选范围；反向滑回时恢复范围外项。
   */
  function updateDragSelect(event) {
    const drag = dragSelectRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    const targetId = getIdFromPoint(dataAttr, event)
    if (!targetId) return
    const targetIndex = getDisplayedIndex(targetId)
    if (targetIndex === -1) return

    if (!drag.started) {
      const dx = event.clientX - drag.startX
      const dy = event.clientY - drag.startY
      if (targetIndex === drag.anchorIndex && dx * dx + dy * dy <= 36) return
      drag.started = true
      dragSelectSuppressClickIdRef.current = drag.anchorId
    }
    event.preventDefault()

    drag.currentIndex = targetIndex
    setSelectedIds(computeRangeSelection({
      items,
      getId,
      anchorIndex: drag.anchorIndex,
      currentIndex: drag.currentIndex,
      snapshot: drag.snapshot,
      preToggledAnchor: drag.preToggledAnchor,
    }))
  }

  /**
   * 结束本次滑选；若真的滑动过，吞掉随后浏览器补发的 click。
   */
  function finishDragSelect(event) {
    const drag = dragSelectRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    if (drag.started) {
      dragSelectSuppressClickIdRef.current = drag.anchorId
    }
    dragSelectRef.current = null
    unlockRootScrollForDragSelect()
  }

  /**
   * 项按下超过 longPressMs 后进入多选状态。
   */
  function handlePointerDown(e, id) {
    clearLongPressTimer()
    longPressTriggeredIdRef.current = null
    longPressStartPosRef.current = null
    if (isSelectMode) {
      startDeferredDragSelect(e, id)
      return
    }
    longPressStartPosRef.current = { x: e.clientX, y: e.clientY }
    longPressPointerInfoRef.current = {
      pointerId: e.pointerId,
      clientX: e.clientX,
      clientY: e.clientY,
      target: e.currentTarget,
    }
    longPressTimerRef.current = setTimeout(() => {
      longPressTimerRef.current = null
      longPressTriggeredIdRef.current = String(id)
      enterSelectMode(id)
      const info = longPressPointerInfoRef.current
      longPressPointerInfoRef.current = null
      if (info) {
        startDragSelect(
          { pointerId: info.pointerId, clientX: info.clientX, clientY: info.clientY, currentTarget: info.target },
          id,
          [String(id)],
          'select',
          true,
        )
      }
    }, longPressMs)
  }

  function handlePointerMove(e) {
    if (dragSelectRef.current) {
      updateDragSelect(e)
      return
    }
    if (!longPressTimerRef.current) return
    const start = longPressStartPosRef.current
    if (!start) return
    const dx = e.clientX - start.x
    const dy = e.clientY - start.y
    if (dx * dx + dy * dy > 36) {
      clearLongPressTimer()
    }
  }

  /**
   * 项正常松手时取消长按计时。
   */
  function handlePointerUp(event) {
    clearLongPressTimer()
    finishDragSelect(event)
  }

  /**
   * 项按压中断时取消长按计时。
   */
  function handlePointerCancel(event) {
    clearLongPressTimer()
    finishDragSelect(event)
  }

  /**
   * 普通长按离开项就取消；滑选已开始时允许继续拖过其他项。
   */
  function handlePointerLeave(event) {
    if (isSelectMode && longPressTimerRef.current) return
    if (dragSelectRef.current) return
    handlePointerCancel(event)
  }

  // 组件卸载时清理计时器和滚动锁，防止内存泄漏
  useEffect(() => () => {
    clearLongPressTimer()
    unlockRootScrollForDragSelect()
  }, [])

  return {
    isSelectMode,
    selectedIds,
    setSelectedIds,
    setIsSelectMode,
    enterSelectMode,
    enterEmptySelectMode,
    exitSelectMode,
    toggleSelected,
    handleToggleSelect,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    handlePointerCancel,
    handlePointerLeave,
  }
}
