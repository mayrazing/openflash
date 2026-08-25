export const MAX_SELECTION_IMAGES = 20
export const MAX_SELECTION_TEXT_LENGTH = 100_000
export const MAX_REMOTE_IMAGE_SOURCE_LENGTH = 8_192
export const MAX_DATA_IMAGE_SOURCE_LENGTH = Math.ceil(8 * 1024 * 1024 * 4 / 3) + 1024
export const MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH = 20 * 1024 * 1024
const MAX_SELECTION_HTML_LENGTH = MAX_DATA_IMAGE_SOURCE_LENGTH * 2 + MAX_SELECTION_TEXT_LENGTH
const MAX_SCANNED_SELECTION_ELEMENTS = 10_000

/**
 * 从 HTML 片段中提取纯文本和图片链接
 * @param {string} html - 选区 HTML 内容
 * @param {string} baseUrl - 当前页面地址，用于把相对图片地址转绝对
 * @returns {{ sideA: string, imageSources: string[] }}
 */
export function extractSelectionFromHtml(html, baseUrl = '') {
  const raw = String(html || '').slice(0, MAX_SELECTION_HTML_LENGTH)

  // 提取所有 <img src="..."> 的 src 属性值，保持顺序（允许 = 两侧空白）
  const imageSources = []
  let retainedSourceLength = 0
  for (const match of raw.matchAll(/<img\b[^>]*\bsrc\s*=\s*["']?([^"'\s>]+)[^>]*>/gi)) {
    const source = normalizeImageSource(decodeHtml(match[1]), baseUrl)
    if (isAllowedImageSource(source)) {
      if (retainedSourceLength + source.length > MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH) break
      imageSources.push(source)
      retainedSourceLength += source.length
    }
    if (imageSources.length >= MAX_SELECTION_IMAGES) break
  }

  // 先移除 <img> 标签（替换为空格），再移除其余 HTML 标签，然后归一化空白
  const sideA = decodeHtml(raw.replace(/<img\b[^>]*>/gi, ' ').replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, MAX_SELECTION_TEXT_LENGTH)

  return { sideA, imageSources }
}

/**
 * 获取浏览器当前选区的纯文本和图片链接（浏览器环境专用）
 * @returns {{ sideA: string, imageSources: string[] }}
 */
export function extractCurrentSelection() {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    return { sideA: '', imageSources: [] }
  }

  const sideA = String(selection.toString?.() || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, MAX_SELECTION_TEXT_LENGTH)
  const imageSources = []
  const seenImages = new Set()
  let scannedElements = 0
  let retainedSourceLength = 0

  ranges: for (let rangeIndex = 0; rangeIndex < selection.rangeCount; rangeIndex += 1) {
    const range = selection.getRangeAt(rangeIndex)
    let root = range.commonAncestorContainer
    if (root?.nodeType === 3) root = root.parentElement
    if (!root) continue

    const walker = document.createTreeWalker(root, globalThis.NodeFilter?.SHOW_ELEMENT || 1)
    let node = root.nodeType === 1 ? root : walker.nextNode()
    while (node) {
      scannedElements += 1
      if (scannedElements > MAX_SCANNED_SELECTION_ELEMENTS) break ranges
      if (String(node.tagName || '').toLowerCase() === 'img' && !seenImages.has(node)) {
        seenImages.add(node)
        let intersects = false
        try {
          intersects = range.intersectsNode(node)
        } catch {
          intersects = false
        }
        if (intersects) {
          const source = normalizeImageSource(
            node.currentSrc || node.getAttribute?.('src') || '',
            document.baseURI,
          )
          if (isAllowedImageSource(source)) {
            if (retainedSourceLength + source.length > MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH) break ranges
            imageSources.push(source)
            retainedSourceLength += source.length
          }
          if (imageSources.length >= MAX_SELECTION_IMAGES) break ranges
        }
      }
      node = walker.nextNode()
    }
  }

  return { sideA, imageSources }
}

/**
 * 把网页里的相对图片地址转换为绝对地址，data/blob 保持原样。
 * @param {string} source
 * @param {string} baseUrl
 * @returns {string}
 */
function normalizeImageSource(source, baseUrl) {
  const value = String(source || '').trim()
  if (!value || /^(data|blob):/i.test(value)) {
    return value
  }
  try {
    return baseUrl ? new URL(value, baseUrl).href : value
  } catch {
    return value
  }
}

function isAllowedImageSource(source) {
  if (/^https?:\/\//i.test(source)) return source.length <= MAX_REMOTE_IMAGE_SOURCE_LENGTH
  if (/^(data|blob):/i.test(source)) return source.length <= MAX_DATA_IMAGE_SOURCE_LENGTH
  return false
}

/**
 * 解码 HTML 实体
 * @param {string} value
 * @returns {string}
 */
function decodeHtml(value) {
  return String(value)
    .replaceAll('&nbsp;', ' ')
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
}
