export function scrollElementIntoRootCenter(root, element, { behavior = 'smooth' } = {}) {
  if (!root || !element || typeof root.scrollTo !== 'function') {
    return false
  }

  const rootRect = root.getBoundingClientRect()
  const elementRect = element.getBoundingClientRect()
  const targetTop = root.scrollTop
    + elementRect.top
    - rootRect.top
    - ((root.clientHeight - element.offsetHeight) / 2)

  root.scrollTo({
    top: Math.max(0, targetTop),
    behavior,
  })
  return true
}
