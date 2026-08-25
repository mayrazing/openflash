import { t } from './i18n.js'

/**
 * 把浏览器右键图片命中的 srcUrl 合并进当前选区，支持单独右键图片导入。
 * @param {{ sideA?: string, imageSources?: string[] }} selection
 * @param {{ mediaType?: string, srcUrl?: string }} info
 * @returns {{ sideA: string, imageSources: string[] }}
 */
export function mergeContextMenuImageSource(selection, info) {
  const sideA = selection?.sideA || ''
  const imageSources = Array.isArray(selection?.imageSources) ? selection.imageSources : []
  const contextImageSource = getContextMenuImageSource(info)
  if (!contextImageSource || imageSources.includes(contextImageSource)) {
    return { sideA, imageSources }
  }
  return { sideA, imageSources: [contextImageSource, ...imageSources] }
}

/**
 * 读取页面选区并合并右键图片；当页面没有内容脚本接收端时，右键图片仍可单独导入。
 * @param {{ mediaType?: string, srcUrl?: string }} info
 * @param {() => Promise<{ sideA?: string, imageSources?: string[] }>} readSelection
 * @param {() => Promise<{ sideA?: string, imageSources?: string[] }>} [fallbackReadSelection]
 * @returns {Promise<{ sideA: string, imageSources: string[] }>}
 */
export async function readSelectionWithContextImage(info, readSelection, fallbackReadSelection) {
  try {
    return mergeContextMenuImageSource(await readSelection(), info)
  } catch (error) {
    if (fallbackReadSelection) {
      try {
        return mergeContextMenuImageSource(await fallbackReadSelection(), info)
      } catch {
        // 继续按右键图片兜底规则处理；没有图片时保留原始选区读取错误。
      }
    }
    if (getContextMenuImageSource(info)) {
      return mergeContextMenuImageSource({ sideA: '', imageSources: [] }, info)
    }
    if (isMissingSelectionReceiverError(error)) {
      throw new Error(t('import.missingReceiver'))
    }
    throw error
  }
}

/**
 * 判断错误是否来自浏览器扩展消息接收端缺失。
 * @param {unknown} error
 * @returns {boolean}
 */
function isMissingSelectionReceiverError(error) {
  return String(error?.message || error).includes('Receiving end does not exist')
}

/**
 * 从浏览器右键菜单事件中提取图片地址，非图片右键返回空字符串。
 * @param {{ mediaType?: string, srcUrl?: string }} info
 * @returns {string}
 */
function getContextMenuImageSource(info) {
  return info?.mediaType === 'image' ? String(info.srcUrl || '').trim() : ''
}
