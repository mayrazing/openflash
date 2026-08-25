export function createSpeakerPressController({
  onShortPress,
  onLongPress,
  longPressMillis = 500,
  movementThreshold = 8,
  setTimeoutFn = setTimeout,
  clearTimeoutFn = clearTimeout,
}) {
  let timer = null
  let pressVersion = 0
  let startPoint = null
  let longPressTriggered = false

  function clearTimer() {
    if (timer !== null) clearTimeoutFn(timer)
    timer = null
  }

  function cancelPendingPress() {
    pressVersion += 1
    clearTimer()
    startPoint = null
  }

  return {
    pointerDown(point) {
      cancelPendingPress()
      startPoint = point
      longPressTriggered = false
      const version = pressVersion
      timer = setTimeoutFn(() => {
        if (version !== pressVersion || !startPoint) return
        timer = null
        longPressTriggered = true
        onLongPress?.()
      }, longPressMillis)
    },

    pointerMove(point) {
      if (!startPoint || longPressTriggered) return
      const distance = Math.hypot(point.x - startPoint.x, point.y - startPoint.y)
      if (distance > movementThreshold) cancelPendingPress()
    },

    pointerEnd() {
      cancelPendingPress()
    },

    pointerCancel() {
      cancelPendingPress()
      longPressTriggered = false
    },

    click() {
      if (longPressTriggered) {
        longPressTriggered = false
        return
      }
      onShortPress?.()
    },
  }
}
