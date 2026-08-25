import { Navbar } from 'konsta/react'

const TRANSPARENT_COLORS = {
  bgIos: 'bg-transparent dark:bg-transparent',
  bgMaterial: 'bg-transparent dark:bg-transparent',
}

/**
 * 全局统一的 Konsta 顶部导航栏。页面背景由 AppPage 负责，导航栏本身不再
 * 叠加 iOS/Material 默认的深色表面或渐变。
 */
export default function AppNavbar({ colors, ...props }) {
  return (
    <Navbar
      {...props}
      colors={{ ...TRANSPARENT_COLORS, ...colors }}
    />
  )
}
