import { useCallback, useEffect, useRef } from 'react'
import { compressImage, uploadImage } from '../lib/imageUtils'
import { appError } from '../lib/appLog'
import { imageToken, splitFaceContent } from '../lib/richFaceOrder'

export default function RichEditor({ initialText = '', initialImages = [], onChange, placeholder }) {
    const editorRef = useRef(null)
    const onChangeRef = useRef(onChange)
    const initialTextRef = useRef(initialText)
    const initialImagesRef = useRef(initialImages)
    const extract = useCallback(function extract() {
        const el = editorRef.current
        if (!el) return { text: '', images: [] }
        const images = []
        const text = readRichEditorNode(el, images)
            .replace(/[^\S\r\n]+/g, ' ')
            .replace(/\n{3,}/g, '\n\n')
            .trim()
        return { text, images }
    }, [])

    function insertImgAtCursor(src) {
        const sel = window.getSelection()
        const img = makeImgNode(src)
        if (sel && sel.rangeCount > 0) {
            const range = sel.getRangeAt(0)
            if (editorRef.current?.contains(range.commonAncestorContainer)) {
                range.deleteContents(); range.insertNode(img)
                range.setStartAfter(img); range.collapse(true)
                sel.removeAllRanges(); sel.addRange(range)
                return
            }
        }
        editorRef.current?.appendChild(img)
    }

    const makeImgNode = useCallback(function makeImgNode(src) {
        const wrapper = document.createElement('span')
        wrapper.contentEditable = 'false'
        wrapper.style.cssText = 'display:inline-block;position:relative;margin:2px 4px;vertical-align:middle;'

        const img = document.createElement('img')
        img.src = src
        img.setAttribute('data-rich', '1')
        img.style.cssText = 'max-height:160px;max-width:100%;border-radius:8px;display:block;'

        const del = document.createElement('button')
        del.type = 'button'; del.textContent = '✕'
        del.style.cssText = 'position:absolute;top:4px;right:4px;background:var(--app-danger-fill);color:var(--app-on-danger);border:none;border-radius:6px;width:20px;height:20px;font-size:11px;cursor:pointer;line-height:1;padding:0;'
        del.addEventListener('click', () => { wrapper.remove(); onChangeRef.current?.(extract()) })

        wrapper.appendChild(img); wrapper.appendChild(del)
        return wrapper
    }, [extract])

    useEffect(() => { onChangeRef.current = onChange }, [onChange])

    useEffect(() => {
        const el = editorRef.current
        if (!el) return
        const initialText = initialTextRef.current
        const initialImages = initialImagesRef.current
        el.innerHTML = ''
        splitFaceContent(initialText, initialImages).forEach((segment) => {
            if (segment.type === 'image') {
                el.appendChild(makeImgNode(segment.src))
            } else {
                appendTextSegment(el, segment.text)
            }
        })
    }, [makeImgNode])

    function handleInput() { onChangeRef.current?.(extract()) }

    async function handlePaste(e) {
        const items = Array.from(e.clipboardData?.items ?? [])
        const imageItems = items.filter((item) => item.type.startsWith('image/'))
        if (imageItems.length === 0) return
        e.preventDefault()
        for (const item of imageItems) {
            const file = item.getAsFile()
            if (!file) continue
            try {
                const base64 = await compressImage(file)
                const url = await uploadImage(base64)
                insertImgAtCursor(url)
            } catch (err) {
                appError(err?.code ?? 50000, '图片上传失败', err)
            }
        }
        onChangeRef.current?.(extract())
    }

    return (
        <div className="relative">
            <div
                ref={editorRef}
                contentEditable
                suppressContentEditableWarning
                onInput={handleInput}
                onPaste={handlePaste}
                data-placeholder={placeholder}
                className={[
                    'min-h-[80px] w-full rounded-xl px-3 py-2 text-base outline-none leading-relaxed break-words',
                    'border border-app-control',
                    'bg-app-surface-primary',
                    'text-app-label-primary',
                    'focus:border-app-focus',
                    'empty:before:content-[attr(data-placeholder)]',
                    'empty:before:text-app-label-tertiary',
                    'empty:before:pointer-events-none',
                ].join(' ')}
            />
        </div>
    )
}

function appendTextSegment(el, text) {
    String(text || '').split('\n').forEach((line, index) => {
        if (index > 0) el.appendChild(document.createElement('br'))
        if (line) el.appendChild(document.createTextNode(line))
    })
}

function readRichEditorNode(node, images) {
    if (!node) return ''
    if (node.nodeType === 3) return node.textContent || ''
    if (node.nodeType !== 1) return ''
    if (node.matches?.('img[data-rich]')) {
        images.push(normalizeImageSrc(node.src))
        return imageToken(images.length - 1)
    }
    if (node.tagName === 'BR') return '\n'
    if (node.tagName === 'BUTTON') return ''
    return Array.from(node.childNodes || []).map((child) => readRichEditorNode(child, images)).join('')
}

function normalizeImageSrc(src) {
    try {
        return new URL(src).pathname
    } catch {
        return src
    }
}
