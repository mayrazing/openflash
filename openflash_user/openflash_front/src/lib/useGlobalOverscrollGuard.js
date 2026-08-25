import { useEffect } from 'react'

const ACTIVATION_TARGET_SELECTOR = [
  'button',
  'a[href]',
  'input',
  'select',
  'textarea',
  '[role="button"]',
  '[role="link"]',
  '[data-pointer-activation]',
].join(',')
const TAP_DRIFT_PX = 12

function shouldGuardOverscroll() {
  const isAppleTouchDevice = /iP(ad|hone|od)/.test(window.navigator.userAgent)
    || (window.navigator.platform === 'MacIntel' && window.navigator.maxTouchPoints > 1)

  return isAppleTouchDevice
}

function findActivationTarget(target) {
  if (!target?.closest) return null
  const activationTarget = target.closest(ACTIVATION_TARGET_SELECTOR)
  if (!activationTarget) return null
  if (activationTarget.disabled) return null
  if (activationTarget.getAttribute?.('aria-disabled') === 'true') return null
  return activationTarget
}

function isTouchStillOnActivationTarget(activationTarget, touch) {
  const elementAtPoint = document.elementFromPoint?.(touch.clientX, touch.clientY)
  if (!elementAtPoint) return true
  return activationTarget.contains?.(elementAtPoint) === true
}

function isScrollableY(element) {
  const style = window.getComputedStyle(element)
  const overflowY = style.overflowY
  return /(auto|scroll|overlay)/.test(overflowY) && element.scrollHeight > element.clientHeight + 1
}

// 在 iPad/WebKit 上拦截根级橡皮筋回弹，同时放过内部正常滚动容器。
function findScrollableAncestor(startElement, rootElement) {
  let current = startElement instanceof Element ? startElement : null

  while (current) {
    if (current === rootElement) {
      return rootElement
    }
    if (isScrollableY(current)) {
      return current
    }
    current = current.parentElement
  }

  return rootElement
}

export default function useGlobalOverscrollGuard() {
  useEffect(() => {
    if (!shouldGuardOverscroll()) {
      return undefined
    }

    const rootElement = document.getElementById('root')
    if (!rootElement) {
      return undefined
    }

    let lastTouchY = 0
    let tapActivationTarget = null
    let tapStartX = 0
    let tapStartY = 0

    function clearTapCandidate() {
      tapActivationTarget = null
      tapStartX = 0
      tapStartY = 0
    }

    function shouldPreserveTap(touch) {
      if (!tapActivationTarget) return false
      const dx = touch.clientX - tapStartX
      const dy = touch.clientY - tapStartY
      if (dx * dx + dy * dy > TAP_DRIFT_PX * TAP_DRIFT_PX) {
        clearTapCandidate()
        return false
      }
      if (!isTouchStillOnActivationTarget(tapActivationTarget, touch)) {
        clearTapCandidate()
        return false
      }
      return true
    }

    function handleTouchStart(event) {
      if (event.touches.length !== 1) {
        clearTapCandidate()
        return
      }
      const touch = event.touches[0]
      lastTouchY = touch.clientY
      tapActivationTarget = findActivationTarget(event.target)
      tapStartX = touch.clientX
      tapStartY = touch.clientY
    }

    function handleTouchMove(event) {
      if (event.touches.length !== 1 || !event.cancelable) {
        if (event.touches.length !== 1) clearTapCandidate()
        return
      }

      const touch = event.touches[0]
      const currentTouchY = touch.clientY
      const deltaY = currentTouchY - lastTouchY
      lastTouchY = currentTouchY

      if (shouldPreserveTap(touch)) {
        return
      }

      const scrollableAncestor = findScrollableAncestor(event.target, rootElement)
      if (!scrollableAncestor) {
        return
      }

      const scrollTop = scrollableAncestor.scrollTop
      const maxScrollTop = scrollableAncestor.scrollHeight - scrollableAncestor.clientHeight
      const isAtTop = scrollTop <= 0
      const isAtBottom = maxScrollTop <= 0 || scrollTop >= maxScrollTop - 1
      const isPullingDown = deltaY > 0
      const isPushingUp = deltaY < 0

      if ((isAtTop && isPullingDown) || (isAtBottom && isPushingUp)) {
        event.preventDefault()
      }
    }

    document.addEventListener('touchstart', handleTouchStart, { passive: true })
    document.addEventListener('touchmove', handleTouchMove, { passive: false })
    document.addEventListener('touchend', clearTapCandidate, { passive: true })
    document.addEventListener('touchcancel', clearTapCandidate, { passive: true })

    return () => {
      document.removeEventListener('touchstart', handleTouchStart)
      document.removeEventListener('touchmove', handleTouchMove)
      document.removeEventListener('touchend', clearTapCandidate)
      document.removeEventListener('touchcancel', clearTapCandidate)
    }
  }, [])
}
