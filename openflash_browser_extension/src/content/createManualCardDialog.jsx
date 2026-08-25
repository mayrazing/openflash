import ManualCardWindow from './ManualCardWindow.jsx'
import { createShadowKonstaRoot } from '../ui/createShadowKonstaRoot.jsx'

const WIDTH = 440
const HEIGHT = 360
const MARGIN = 16
const ISOLATED_EVENTS = [
  'beforeinput',
  'click',
  'compositionend',
  'compositionstart',
  'dblclick',
  'focusin',
  'focusout',
  'input',
  'keydown',
  'keypress',
  'keyup',
  'mousedown',
  'mouseup',
  'paste',
  'pointerdown',
  'pointermove',
  'pointerup',
  'touchend',
  'touchstart',
]

/** 创建手动建卡窗口控制器。 */
export function createManualCardDialog(deps = {}) {
  const pageDocument = deps.document || document
  const pageWindow = deps.window || window
  const chromeApi = deps.chromeApi || chrome
  const editor = deps.editor || globalThis.OpenFlashManualCardEditor
  const createSaver = deps.createSaver || globalThis.OpenFlashManualCardSave.createSaver
  const createShadowRoot = deps.createShadowRoot || createShadowKonstaRoot
  let host = null
  let state = null
  let mode = 'edit'
  let saver = null
  let current = null
  let konstaRoot = null
  let dragStart = null

  /** 打开新窗口；已有窗口只聚焦。 */
  async function open(context) {
    current = context
    if (host) {
      host.focus()
      return { ok: true }
    }

    const selectedText = typeof context?.selectedText === 'string'
      ? context.selectedText
      : String(pageWindow.getSelection?.()?.toString?.() || '')
    state = editor.createState(selectedText)
    saver = createSaver()
    host = pageDocument.createElement('div')
    host.tabIndex = -1
    host.style.position = 'fixed'
    host.style.zIndex = '2147483647'
    host.style.width = `min(${WIDTH}px, calc(100vw - ${MARGIN * 2}px))`
    const position = clampPosition(await readPosition())
    host.style.left = `${position.left}px`
    host.style.top = `${position.top}px`
    konstaRoot = createShadowRoot(host)
    installRootListeners(konstaRoot.shadowRoot)
    pageDocument.body.appendChild(host)
    render()
    konstaRoot.shadowRoot.querySelector('[data-side="a"]')?.focus()
    return { ok: true }
  }

  /** 卸载 React 根节点并丢弃本次窗口状态。 */
  function close() {
    const wasOpen = Boolean(host)
    konstaRoot?.unmount()
    host?.remove()
    host = null
    state = null
    current = null
    saver = null
    konstaRoot = null
    dragStart = null
    mode = 'edit'
    if (wasOpen) deps.onClosed?.()
  }

  function render() {
    konstaRoot.render(
      <ManualCardWindow
        state={state}
        mode={mode}
        labels={current.labels || {}}
        onCancel={requestClose}
        onSave={save}
        onConfirmBack={() => setMode('edit')}
        onConfirmClose={close}
        onEditorInput={syncSideFromEditor}
        onEditorPaste={insertPastedImages}
        onRemoveImage={removeImage}
      />,
    )
  }

  function setMode(nextMode) {
    mode = nextMode
    render()
  }

  function requestClose() {
    if (editor.hasAnyContent(state)) {
      setMode('confirm')
      return
    }
    close()
  }

  function handleEscape() {
    if (mode === 'confirm') {
      setMode('edit')
      return
    }
    requestClose()
  }

  async function readPosition() {
    const data = await chromeApi.storage.local.get('manualCardPosition')
    return data.manualCardPosition || null
  }

  async function writePosition(position) {
    await chromeApi.storage.local.set({ manualCardPosition: clampPosition(position) })
  }

  function clampPosition(position) {
    const availableWidth = Math.min(WIDTH, Math.max(0, pageWindow.innerWidth - MARGIN * 2))
    const fallback = {
      left: Math.round((pageWindow.innerWidth - availableWidth) / 2),
      top: Math.round((pageWindow.innerHeight - HEIGHT) / 2),
    }
    const source = position || fallback
    return {
      left: Math.min(Math.max(MARGIN, Number(source.left) || fallback.left), Math.max(MARGIN, pageWindow.innerWidth - availableWidth)),
      top: Math.min(Math.max(MARGIN, Number(source.top) || fallback.top), Math.max(MARGIN, pageWindow.innerHeight - HEIGHT - 20)),
    }
  }

  function installRootListeners(root) {
    root.addEventListener('keydown', handleRootKeydown)
    root.addEventListener('compositionstart', handleCompositionStart)
    root.addEventListener('compositionend', handleCompositionEnd)
    root.addEventListener('pointerdown', startDrag)
    root.addEventListener('pointermove', moveDrag)
    root.addEventListener('pointerup', endDrag)
    ISOLATED_EVENTS.forEach((type) => root.addEventListener(type, stopEventPropagation))
  }

  function stopEventPropagation(event) {
    event.stopPropagation?.()
  }

  function handleRootKeydown(event) {
    if (event.key === 'Escape') {
      event.preventDefault()
      handleEscape()
      return
    }
    if (event.key === 'Enter' && !event.shiftKey && !state.composing && event.target?.closest?.('[data-side]')) {
      event.preventDefault()
      save()
    }
  }

  function handleCompositionStart(event) {
    if (event.target?.closest?.('[data-side]')) state.composing = true
  }

  function handleCompositionEnd(event) {
    if (event.target?.closest?.('[data-side]')) state.composing = false
  }

  function syncSideFromEditor(sideKey, editorNode) {
    const node = editorNode || konstaRoot.shadowRoot.querySelector(`[data-side="${sideKey}"]`)
    const value = editor.readSideFromEditor(node)
    state[sideKey].text = value.text
    state[sideKey].imageOrder = value.imageOrder
  }

  function insertPastedImages(sideKey, event) {
    const images = editor.imagesFromClipboardItems(event.clipboardData?.items)
    if (!images.length) return
    event.preventDefault()
    const currentImageCount = state.a.images.length + state.b.images.length
    const maxImageCount = globalThis.OpenFlashManualCardImageProcessor?.DEFAULT_LIMITS?.maxImageCount || 10
    if (currentImageCount + images.length > maxImageCount) {
      state.error = 'manualCard.tooManyImages'
      render()
      return
    }
    state.error = ''
    state[sideKey].images.push(...images)
    images.forEach((image) => insertImageChipAtCursor(event.currentTarget, sideKey, image))
    syncSideFromEditor(sideKey, event.currentTarget)
  }

  function removeImage(event) {
    const button = event.target?.closest?.('[data-remove-image]')
    if (!button || !state) return
    const sideKey = button.dataset.sideKey
    const side = state[sideKey]
    if (!side) return
    side.images = side.images.filter((image) => image.id !== button.dataset.removeImage)
    side.imageOrder = side.imageOrder.filter((id) => id !== button.dataset.removeImage)
    state.error = ''
    const chip = button.closest('[data-openflash-image-id]')
    const anchor = chip?.nextElementSibling?.classList?.contains('caret-anchor') ? chip.nextElementSibling : null
    chip?.remove()
    anchor?.remove()
    syncSideFromEditor(sideKey)
  }

  function insertImageChipAtCursor(editorNode, sideKey, image) {
    const wrapper = pageDocument.createElement('span')
    wrapper.innerHTML = imageChipHtml(sideKey, image)
    const chip = wrapper.firstElementChild
    const anchor = wrapper.querySelector('.caret-anchor')
    const selection = pageWindow.getSelection?.()
    if (selection?.rangeCount) {
      const range = selection.getRangeAt(0)
      if (editorNode.contains(range.commonAncestorContainer)) {
        range.deleteContents()
        const fragment = pageDocument.createDocumentFragment()
        fragment.appendChild(chip)
        if (anchor) fragment.appendChild(anchor)
        range.insertNode(fragment)
        moveCaretAfter(editorNode, anchor || chip)
        return
      }
    }
    editorNode.appendChild(chip)
    if (anchor) editorNode.appendChild(anchor)
    moveCaretAfter(editorNode, anchor || chip)
  }

  function imageChipHtml(sideKey, image) {
    return `
      <span class="image relative mx-[3px] mb-[3px] inline-block align-middle" contenteditable="false" data-openflash-image-id="${escapeHtml(image.id)}">
        <img class="block h-[72px] w-[72px] rounded-[6px] object-cover" src="${escapeHtml(image.previewUrl || '')}" alt="">
        <button class="absolute right-0.5 top-0.5 h-[18px] w-[18px] cursor-pointer rounded-[6px] border-0 bg-app-danger-fill p-0 text-xs leading-[18px] text-app-on-danger" data-remove-image="${escapeHtml(image.id)}" data-side-key="${sideKey}" type="button">x</button>
      </span>
      <span class="caret-anchor inline leading-5">\u200B</span>
    `
  }

  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
  }

  function moveCaretAfter(editorNode, node) {
    if (!node) return
    editorNode.focus()
    const selection = pageWindow.getSelection?.()
    if (!selection) return
    const nextRange = pageDocument.createRange()
    nextRange.setStartAfter(node)
    nextRange.collapse(true)
    selection.removeAllRanges()
    selection.addRange(nextRange)
  }

  async function save() {
    if (state.saving) return
    state.saving = true
    syncSideFromEditor('a')
    syncSideFromEditor('b')
    render()
    const snapshot = {
      a: { text: state.a.text, images: state.a.images, imageOrder: [...state.a.imageOrder] },
      b: { text: state.b.text, images: state.b.images, imageOrder: [...state.b.imageOrder] },
    }
    try {
      await saver.save({ baseUrl: current.baseUrl, deckId: current.deckId, state: snapshot })
      if (!host) return
      await chromeApi.runtime.sendMessage({
        type: 'OPENFLASH_NOTIFY_ACTIVE_TAB',
        message: translate('manualCard.saved'),
        level: 'success',
        sourceTabId: current.sourceTabId,
      }).catch(() => {})
      close()
    } catch (error) {
      if (!host) return
      state.error = resolveSaveErrorMessage(error)
      state.saving = false
      render()
    }
  }

  function translate(key) {
    return current?.labels?.[key] || key
  }

  function resolveSaveErrorMessage(error) {
    if (error?.message === 'manualCard.emptyContent') return 'manualCard.emptyContent'
    return error?.message || 'manualCard.saveFailed'
  }

  function startDrag(event) {
    const title = event.target?.closest?.('[data-role="drag"]')
    if (!title) return
    dragStart = {
      x: event.clientX,
      y: event.clientY,
      left: parseInt(host.style.left, 10),
      top: parseInt(host.style.top, 10),
    }
    title.setPointerCapture?.(event.pointerId)
  }

  function moveDrag(event) {
    if (!dragStart) return
    const next = clampPosition({
      left: dragStart.left + event.clientX - dragStart.x,
      top: dragStart.top + event.clientY - dragStart.y,
    })
    host.style.left = `${next.left}px`
    host.style.top = `${next.top}px`
  }

  function endDrag() {
    if (!dragStart) return
    dragStart = null
    writePosition({ left: parseInt(host.style.left, 10), top: parseInt(host.style.top, 10) })
  }

  return {
    open,
    close,
    handleEscapeForTest: handleEscape,
    handleDragEndForTest: writePosition,
    getModeForTest: () => mode,
    setStateForTest: (partial) => { state = { ...state, ...partial } },
  }
}
