export const SHORTCUT_SETTINGS_URL = 'chrome://extensions/shortcuts'

/**
 * 打开浏览器自带的扩展快捷键设置页，让用户手动绑定 OpenFlash 命令。
 * 浏览器不允许扩展直接替用户写入快捷键，所以这里仅提供入口。
 */
export function openBrowserShortcutSettings(tabs) {
  return tabs.create({ url: SHORTCUT_SETTINGS_URL })
}