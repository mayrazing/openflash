import { ROOT_MENU_ID } from './config.js'

export const IMPORT_DEFAULT_COMMAND = 'openflash-import-default'

/** 处理浏览器快捷键导入默认卡包。 */
export function createCommandImportHandler(importMenus) {
  return function handleCommandImport(command, tab) {
    if (command !== IMPORT_DEFAULT_COMMAND) {
      return Promise.resolve(false)
    }
    return importMenus.handleMenuClick({ menuItemId: ROOT_MENU_ID }, tab)
  }
}
