import { createContext, useContext } from 'react'

export const ThemeContext = createContext({ theme: 'light', toggleTheme: () => {} })

// 未登录时读取当前系统外观, 避免匿名页面固定成明色.
export function getSystemTheme(matchMedia = query => window.matchMedia(query)) {
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function useTheme() {
  return useContext(ThemeContext)
}
