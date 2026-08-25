export function resolvePopupErrorMessage(error, fallback) {
  return typeof error === 'string' ? error : error?.message || fallback
}

export function resolveSessionErrorMessage(error, fallback) {
  return error?.code === 40101 ? '' : resolvePopupErrorMessage(error, fallback)
}
