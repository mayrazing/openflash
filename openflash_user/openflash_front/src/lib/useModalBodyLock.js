import { useEffect } from 'react'

let lockCount = 0
let savedScrollY = 0
let savedAppRootScrollTop = 0
let savedBodyStyles = null
let savedRootStyles = null
let savedAppRootStyles = null
let lastTouchY = 0

function applyLock() {
  const body = document.body
  const appRoot = document.getElementById('root')
  body.style.overflow = 'hidden'
  body.style.position = 'fixed'
  body.style.top = `-${savedScrollY}px`
  body.style.width = '100%'
  body.style.height = '100dvh'
  document.documentElement.style.overflow = 'hidden'
  document.documentElement.style.overscrollBehavior = 'none'
  if (appRoot) {
    appRoot.style.overflowY = 'hidden'
    appRoot.style.overscrollBehavior = 'none'
  }
}

function getOverflowY(element) {
  if (typeof window.getComputedStyle === 'function') {
    return window.getComputedStyle(element).overflowY
  }
  return element?.style?.overflowY ?? ''
}

function isScrollableY(element) {
  if (!element) return false
  const overflowY = getOverflowY(element)
  return /(auto|scroll|overlay)/.test(overflowY) && element.scrollHeight > element.clientHeight + 1
}

function findScrollableAncestor(startElement) {
  const appRoot = document.getElementById('root')
  let current = startElement && typeof startElement === 'object' ? startElement : null

  while (current) {
    if (current === appRoot || current === document.body || current === document.documentElement) {
      return null
    }
    if (isScrollableY(current)) {
      return current
    }
    current = current.parentElement
  }

  return null
}

function canScrollWithTouchDelta(element, deltaY) {
  const maxScrollTop = element.scrollHeight - element.clientHeight
  const isPullingDown = deltaY > 0
  const isPushingUp = deltaY < 0

  if (isPullingDown) {
    return element.scrollTop > 0
  }
  if (isPushingUp) {
    return element.scrollTop < maxScrollTop - 1
  }
  return false
}

function handleTouchStart(event) {
  if (event.touches.length !== 1) {
    return
  }
  lastTouchY = event.touches[0].clientY
}

function handleTouchMove(event) {
  if (!event.cancelable) {
    return
  }

  if (event.touches.length !== 1) {
    event.preventDefault()
    return
  }

  const currentTouchY = event.touches[0].clientY
  const deltaY = currentTouchY - lastTouchY
  lastTouchY = currentTouchY

  const scrollableAncestor = findScrollableAncestor(event.target)
  if (!scrollableAncestor || !canScrollWithTouchDelta(scrollableAncestor, deltaY)) {
    event.preventDefault()
  }
}

export default function useModalBodyLock(locked) {
  useEffect(() => {
    if (!locked) return undefined

    lockCount += 1
    if (lockCount === 1) {
      const body = document.body
      const root = document.documentElement
      const appRoot = document.getElementById('root')

      savedScrollY = window.scrollY
      savedAppRootScrollTop = appRoot?.scrollTop ?? 0
      savedBodyStyles = {
        overflow: body.style.overflow,
        position: body.style.position,
        top: body.style.top,
        width: body.style.width,
        height: body.style.height,
      }
      savedRootStyles = {
        overflow: root.style.overflow,
        overscrollBehavior: root.style.overscrollBehavior,
      }
      savedAppRootStyles = appRoot
        ? {
            overflowY: appRoot.style.overflowY,
            overscrollBehavior: appRoot.style.overscrollBehavior,
          }
        : null

      applyLock()
      window.addEventListener('resize', applyLock)
      document.addEventListener('touchstart', handleTouchStart, { passive: true })
      document.addEventListener('touchmove', handleTouchMove, { passive: false })
    }

    return () => {
      lockCount -= 1
      if (lockCount > 0) {
        return
      }

      window.removeEventListener('resize', applyLock)
      document.removeEventListener('touchstart', handleTouchStart)
      document.removeEventListener('touchmove', handleTouchMove)

      const body = document.body
      const root = document.documentElement
      const appRoot = document.getElementById('root')

      body.style.overflow = savedBodyStyles?.overflow ?? ''
      body.style.position = savedBodyStyles?.position ?? ''
      body.style.top = savedBodyStyles?.top ?? ''
      body.style.width = savedBodyStyles?.width ?? ''
      body.style.height = savedBodyStyles?.height ?? ''
      root.style.overflow = savedRootStyles?.overflow ?? ''
      root.style.overscrollBehavior = savedRootStyles?.overscrollBehavior ?? ''
      if (appRoot && savedAppRootStyles) {
        appRoot.style.overflowY = savedAppRootStyles.overflowY ?? ''
        appRoot.style.overscrollBehavior = savedAppRootStyles.overscrollBehavior ?? ''
        appRoot.scrollTop = savedAppRootScrollTop
      } else {
        window.scrollTo(0, savedScrollY)
      }

      savedScrollY = 0
      savedAppRootScrollTop = 0
      savedBodyStyles = null
      savedRootStyles = null
      savedAppRootStyles = null
    }
  }, [locked])
}
