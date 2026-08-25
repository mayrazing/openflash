(() => {
  let nextImageId = 1
  const IMAGE_TOKEN_PREFIX = '\uE000OFIMG:'
  const IMAGE_TOKEN_SUFFIX = '\uE000'

  /** 创建手动建卡状态；选中文字预填 A 面。 */
  function createState(selectedText = '') {
    return {
      a: { text: String(selectedText || ''), images: [], imageOrder: [] },
      b: { text: '', images: [], imageOrder: [] },
      composing: false,
      saving: false,
      error: '',
    }
  }

  /** 判断整张卡是否有任意文本或图片。 */
  function hasAnyContent(state) {
    return Boolean(
      String(state?.a?.text || '').trim()
      || String(state?.b?.text || '').trim()
      || state?.a?.images?.length
      || state?.b?.images?.length
    )
  }

  /** 根据上传 URL 缓存构造后端 payload。 */
  function buildPayload(state, uploadedByImageId) {
    return {
      sideA: String(state.a.text || '').trim(),
      sideAImage: state.a.imageOrder.map((id) => uploadedByImageId[id]).filter(Boolean),
      sideB: String(state.b.text || '').trim(),
      sideBImage: state.b.imageOrder.map((id) => uploadedByImageId[id]).filter(Boolean),
    }
  }

  /** 从剪贴板中提取图片，生成本地预览对象。 */
  function imagesFromClipboardItems(items, createObjectURL) {
    const makeUrl = createObjectURL || globalThis.URL?.createObjectURL?.bind(globalThis.URL)
    return Array.from(items || [])
      .filter((item) => item.type?.startsWith('image/'))
      .map((item) => {
        const file = item.getAsFile()
        if (!file) return null
        return { id: `manual-image-${nextImageId++}`, file, previewUrl: makeUrl ? makeUrl(file) : '' }
      })
      .filter(Boolean)
  }

  /** 从 contenteditable DOM 读取纯文本和图片 chip 顺序。 */
  function readSideFromEditor(editorNode) {
    const imageOrder = []
    const text = readInlineContent(editorNode, imageOrder)
      .replace(/\u200B/g, '')
      .replace(/[^\S\r\n]+/g, ' ')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
    return {
      text,
      imageOrder,
    }
  }

  function readInlineContent(node, imageOrder) {
    if (!node) return ''
    if (node.dataset?.openflashImageId) {
      const index = imageOrder.length
      imageOrder.push(node.dataset.openflashImageId)
      return `${IMAGE_TOKEN_PREFIX}${index}${IMAGE_TOKEN_SUFFIX}`
    }
    if (node.nodeType === 3) return node.textContent || ''
    if (node.tagName === 'BR') return '\n'
    return Array.from(node.childNodes || []).map((child) => readInlineContent(child, imageOrder)).join('')
  }

  globalThis.OpenFlashManualCardEditor = {
    createState,
    hasAnyContent,
    buildPayload,
    imagesFromClipboardItems,
    readSideFromEditor,
  }
})()
