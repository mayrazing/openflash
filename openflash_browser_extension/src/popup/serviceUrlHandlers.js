export function createServiceUrlHandlers(setServiceUrlAction) {
  const persistCurrentValue = (event) => setServiceUrlAction(event.currentTarget.value)
  return {
    onBlur: persistCurrentValue,
    onInput: persistCurrentValue,
  }
}
