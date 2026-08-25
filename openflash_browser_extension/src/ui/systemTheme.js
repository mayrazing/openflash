export function watchSystemTheme(target, matchMedia = window.matchMedia.bind(window)) {
  const query = matchMedia('(prefers-color-scheme: dark)')
  const apply = () => target.classList.toggle('dark', query.matches)
  apply()
  query.addEventListener?.('change', apply)
  return () => query.removeEventListener?.('change', apply)
}
