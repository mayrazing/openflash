import { NavbarBackLink } from 'konsta/react'

/**
 * Konsta 默认把无 href 的返回入口渲染成 a, 键盘无法触发点击.
 * 使用原生 button 保留 Konsta 外观, 同时恢复 Enter/Space 操作.
 */
export default function AppNavbarBackLink(props) {
  return <NavbarBackLink component="button" type="button" {...props} />
}
