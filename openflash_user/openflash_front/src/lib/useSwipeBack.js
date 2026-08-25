import { useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'

const NO_SWIPE_BACK_PATHS = ['/', '/auth']

// 按路由层级返回父页面：/deck/:id/* → /deck/:id，其余 → /
function getParentPath(pathname) {
  const match = pathname.match(/^(\/deck\/[^/]+)\//)
  if (match) return match[1]
  return '/'
}

// 从左边缘右滑返回上一页，模拟 iOS 手势。
export default function useSwipeBack() {
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    if (NO_SWIPE_BACK_PATHS.includes(location.pathname)) return

    let startX = null
    let startY = null

    function onTouchStart(e) {
      const touch = e.touches[0]
      if (touch.clientX > 40) return
      startX = touch.clientX
      startY = touch.clientY
    }

    function onTouchEnd(e) {
      if (startX === null) return
      const touch = e.changedTouches[0]
      const dx = touch.clientX - startX
      const dy = Math.abs(touch.clientY - startY)
      startX = null
      startY = null
      if (dx > 80 && dy < dx) navigate(getParentPath(location.pathname))
    }

    function onTouchCancel() {
      startX = null
      startY = null
    }

    document.addEventListener('touchstart', onTouchStart, { passive: true })
    document.addEventListener('touchend', onTouchEnd, { passive: true })
    document.addEventListener('touchcancel', onTouchCancel, { passive: true })

    return () => {
      document.removeEventListener('touchstart', onTouchStart)
      document.removeEventListener('touchend', onTouchEnd)
      document.removeEventListener('touchcancel', onTouchCancel)
    }
  }, [navigate, location.pathname])
}
