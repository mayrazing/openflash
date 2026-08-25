import { createElement, Fragment, useEffect, useRef, useState } from 'react'
import { playGenericClick } from './soundEngine.js'

const SIDE_HINT_MIN_VISIBLE_MS = 280
const SIDE_HINT_MAX_PRESS_MS = 400

// 根据指针位置判断用户是否点在页面右侧上/下快捷滚动区域。
export function getSideHintFromPointer(event, win = globalThis.window) {
  if (!win || event.target !== event.currentTarget || event.clientX <= win.innerWidth / 2) return null
  if (event.clientY < win.innerHeight / 4) return 'top'
  if (event.clientY > win.innerHeight * 3 / 4) return 'bottom'
  return null
}

// 创建不依赖 React 的右侧滚动提示控制器，统一处理按下、松开、清理反馈。
export function createSideScrollHintController({
  win = globalThis.window,
  now = () => Date.now(),
  setTimer = (fn, delay) => setTimeout(fn, delay),
  clearTimer = id => clearTimeout(id),
  onFeedback = () => {},
  onClearFeedback = () => {},
  onTop = () => {},
  onBottom = () => {},
  onClick = () => {},
  minVisibleMs = SIDE_HINT_MIN_VISIBLE_MS,
  maxPressMs = SIDE_HINT_MAX_PRESS_MS,
} = {}) {
  let press = null
  let timer = null

  // 取事件本身的输入时间戳来量按压时长，规避主线程阻塞导致 now() 被撑大的误判；
  // 缺失 timeStamp 的事件（单测、极老环境）回退 now()，保持原有行为。
  function eventTime(event) {
    return event && typeof event.timeStamp === 'number' && event.timeStamp > 0
      ? event.timeStamp
      : now()
  }

  // 清掉仍在等待的反馈定时器，避免旧点击覆盖新点击状态。
  function clearFeedbackTimer() {
    if (timer) {
      clearTimer(timer)
      timer = null
    }
  }

  // 结束当前反馈状态，供取消、离开和无效松手共用。
  function clear() {
    press = null
    clearFeedbackTimer()
    onClearFeedback()
  }

  return {
    // 按下右侧上/下区域时记录起点并立即显示箭头反馈。
    pointerDown(event) {
      const side = getSideHintFromPointer(event, win)
      if (!side) return
      clearFeedbackTimer()
      // time 用墙钟驱动反馈最短显示时长；eventTime 用输入时间戳判定点击是否超时。
      press = { time: now(), eventTime: eventTime(event), side }
      onFeedback(side)
    },

    // 松手时校验本次点击仍有效，再触发顶部或底部滚动。
    pointerUp(event) {
      if (!press || event.clientX <= win.innerWidth / 2 || eventTime(event) - press.eventTime > maxPressMs) {
        clear()
        return
      }
      onClick()
      if (press.side === 'top') onTop()
      else onBottom()
      const remainingMs = Math.max(minVisibleMs - (now() - press.time), 180)
      press = null
      timer = setTimer(() => {
        onClearFeedback()
        timer = null
      }, remainingMs)
    },

    clear,
  }
}

// 提供页面可直接挂载的右侧滚动处理器和同款上下双箭头渲染函数。
export function useSideScrollHints({ onTop, onBottom, disabled = false } = {}) {
  const [activeSideHint, setActiveSideHint] = useState(null)
  const [sideHintAnimationKey, setSideHintAnimationKey] = useState(0)
  const callbacksRef = useRef({ onTop, onBottom, disabled })
  const controllerRef = useRef(null)

  callbacksRef.current = { onTop, onBottom, disabled }

  if (!controllerRef.current) {
    controllerRef.current = createSideScrollHintController({
      onFeedback: side => {
        setActiveSideHint(side)
        setSideHintAnimationKey(key => key + 1)
      },
      onClearFeedback: () => setActiveSideHint(null),
      onTop: () => callbacksRef.current.onTop?.(),
      onBottom: () => callbacksRef.current.onBottom?.(),
      onClick: () => playGenericClick(),
    })
  }

  useEffect(() => () => controllerRef.current?.clear(), [])

  // 生成箭头的视觉样式，按下的方向更亮并播放弹性动画。
  function sideHintClass(side) {
    const active = activeSideHint === side
    return `fixed pointer-events-none select-none flex justify-center ${
      active
        ? 'side-hint-pulse text-app-focus'
        : 'scale-100 text-app-label-tertiary opacity-40 dark:opacity-15'
    }`
  }

  // 渲染指定方向的双箭头，箭头只显示反馈，不拦截任何点击。
  function renderArrow(side) {
    const isTop = side === 'top'
    return createElement(
      'div',
      {
        key: `${side}-${activeSideHint === side ? sideHintAnimationKey : 0}`,
        className: sideHintClass(side),
        style: {
          [side]: '8vh',
          left: 'calc(50% + 245px)',
          width: 'calc((100vw - 512px) / 2)',
        },
      },
      createElement(
        'svg',
        {
          width: '100',
          height: '130',
          viewBox: '0 0 80 100',
          fill: 'none',
          stroke: 'currentColor',
          strokeWidth: '10',
          strokeLinecap: 'round',
          strokeLinejoin: 'round',
        },
        createElement('polyline', { points: isTop ? '10,48 40,10 70,48' : '10,52 40,90 70,52' }),
        createElement('polyline', { points: isTop ? '10,90 40,52 70,90' : '10,10 40,48 70,10' }),
      ),
    )
  }

  return {
    sideScrollHandlers: {
      onPointerDown: event => {
        if (!callbacksRef.current.disabled) controllerRef.current.pointerDown(event)
      },
      onPointerUp: event => {
        if (callbacksRef.current.disabled) controllerRef.current.clear()
        else controllerRef.current.pointerUp(event)
      },
      onPointerLeave: () => controllerRef.current.clear(),
      onPointerCancel: () => controllerRef.current.clear(),
    },
    renderSideHints: () => createElement(Fragment, null, renderArrow('top'), renderArrow('bottom')),
  }
}
