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

const NATIVE_CLICK_SUPPRESSION_MS = 350
const MAX_PEN_TAP_MS = 500

export function findPointerActivationTarget(target) {
  if (!target?.closest) return null
  const activationTarget = target.closest(ACTIVATION_TARGET_SELECTOR)
  if (!activationTarget) return null
  if (activationTarget.disabled) return null
  if (activationTarget.getAttribute?.('aria-disabled') === 'true') return null
  return activationTarget
}

export function installPointerActivationBridge({
  doc = typeof document === 'undefined' ? null : document,
  now = () => Date.now(),
} = {}) {
  if (!doc?.addEventListener) return () => {}

  let activePenTarget = null
  let activePenPointerId = null
  let activePenStartedAt = 0
  let suppressNativeClickTarget = null
  let suppressNativeClickUntil = 0
  let dispatchingSyntheticClick = false

  function clearActivePen(event) {
    if (activePenPointerId === event.pointerId) {
      activePenPointerId = null
      activePenTarget = null
      activePenStartedAt = 0
    }
  }

  function handlePointerDown(event) {
    if (event.pointerType !== 'pen') return
    const target = findPointerActivationTarget(event.target)
    if (!target) return
    activePenPointerId = event.pointerId
    activePenTarget = target
    activePenStartedAt = event.timeStamp ?? now()
  }

  function handlePointerUp(event) {
    if (event.pointerType !== 'pen') return
    if (activePenPointerId !== event.pointerId) return
    const target = findPointerActivationTarget(event.target)
    if (!target || target !== activePenTarget) {
      clearActivePen(event)
      return
    }
    const duration = (event.timeStamp ?? now()) - activePenStartedAt
    if (duration > MAX_PEN_TAP_MS) {
      clearActivePen(event)
      return
    }

    clearActivePen(event)
    event.preventDefault?.()
    suppressNativeClickTarget = target
    suppressNativeClickUntil = now() + NATIVE_CLICK_SUPPRESSION_MS

    dispatchingSyntheticClick = true
    try {
      target.click()
    } finally {
      dispatchingSyntheticClick = false
    }
  }

  function handleClick(event) {
    if (dispatchingSyntheticClick) return
    if (now() > suppressNativeClickUntil) return
    const target = findPointerActivationTarget(event.target)
    if (!target || target !== suppressNativeClickTarget) return
    event.preventDefault?.()
    event.stopImmediatePropagation?.()
  }

  doc.addEventListener('pointerdown', handlePointerDown, true)
  doc.addEventListener('pointerup', handlePointerUp, true)
  doc.addEventListener('pointercancel', clearActivePen, true)
  doc.addEventListener('pointerleave', clearActivePen, true)
  doc.addEventListener('click', handleClick, true)

  return () => {
    doc.removeEventListener('pointerdown', handlePointerDown, true)
    doc.removeEventListener('pointerup', handlePointerUp, true)
    doc.removeEventListener('pointercancel', clearActivePen, true)
    doc.removeEventListener('pointerleave', clearActivePen, true)
    doc.removeEventListener('click', handleClick, true)
  }
}
