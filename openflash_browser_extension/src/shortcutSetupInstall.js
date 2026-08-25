/**
 * 首次安装扩展时打开快捷键引导页；升级或刷新扩展时不打扰用户。
 */
export function openShortcutSetupOnInstall(details, { runtime, tabs }) {
  if (details?.reason !== 'install') return
  tabs.create({ url: runtime.getURL('shortcutSetup.html') })
}
