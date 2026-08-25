import { useTranslation } from 'react-i18next'
import { parseColloc } from '../lib/collocUtils.js'
import { withGenericClick } from '../lib/soundEngine'

function parseInline(text, keyPrefix) {
  if (!text) return []

  const tokens = []
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g
  let cursor = 0
  let index = 0

  for (const match of text.matchAll(pattern)) {
    const start = match.index ?? 0
    if (start > cursor) {
      tokens.push(text.slice(cursor, start))
    }

    const token = match[0]
    if (token.startsWith('**') && token.endsWith('**')) {
      tokens.push(
        <strong key={`${keyPrefix}-strong-${index}`} className="font-semibold text-app-label-primary">
          {token.slice(2, -2)}
        </strong>
      )
    } else if (token.startsWith('`') && token.endsWith('`')) {
      tokens.push(
        <code
          key={`${keyPrefix}-code-${index}`}
          className="rounded bg-app-code-surface px-1.5 py-0.5 font-mono text-[0.92em] text-app-code-label"
        >
          {token.slice(1, -1)}
        </code>
      )
    } else {
      const link = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      const safeHref = normalizeSafeHref(link?.[2])
      if (!safeHref) {
        tokens.push(link?.[1] ?? token)
      } else {
        tokens.push(
          <a
            key={`${keyPrefix}-link-${index}`}
            href={safeHref}
            target="_blank"
            rel="noreferrer"
            className="text-app-accent underline underline-offset-2"
          >
            {link?.[1] ?? token}
          </a>
        )
      }
    }

    cursor = start + token.length
    index += 1
  }

  if (cursor < text.length) {
    tokens.push(text.slice(cursor))
  }

  return tokens
}

function normalizeSafeHref(href) {
  if (!href) return null

  try {
    const parsed = new URL(href, 'https://pickword.local')
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return href
    }
    return null
  } catch {
    return null
  }
}

/**
 * 以 React 节点渲染常见 Markdown，避免执行原始 HTML。
 */
export default function MarkdownRenderer({ markdown, onCollocClick }) {
  const { t } = useTranslation()
  if (!markdown?.trim()) {
    return <p className="text-sm text-app-label-tertiary">{t('markdown.noContent')}</p>
  }

  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let index = 0
  let codeBlockIndex = 0
  let inColloc = false

  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    if (!trimmed) {
      index += 1
      continue
    }

    if (trimmed.startsWith('```')) {
      const codeLines = []
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index])
        index += 1
      }
      if (index < lines.length) {
        index += 1
      }
      blocks.push(
        <pre
          key={`code-${codeBlockIndex}`}
          className="overflow-x-auto rounded-2xl bg-app-code-surface px-4 py-3 text-xs leading-6 text-app-code-label"
        >
          <code>{codeLines.join('\n')}</code>
        </pre>
      )
      codeBlockIndex += 1
      continue
    }

    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      inColloc = false
      const level = heading[1].length
      const className = level === 1
        ? 'text-xl font-semibold text-app-label-primary'
        : level === 2
          ? 'text-lg font-semibold text-app-label-primary'
          : 'text-base font-semibold text-app-label-primary'
      blocks.push(
        <div key={`heading-${index}`} className={className}>
          {parseInline(heading[2], `heading-${index}`)}
        </div>
      )
      index += 1
      continue
    }

    const quote = trimmed.match(/^>\s?(.*)$/)
    if (quote) {
      blocks.push(
        <blockquote
          key={`quote-${index}`}
          className="border-l-4 border-app-separator pl-4 text-base leading-7 text-app-label-secondary"
        >
          {parseInline(quote[1], `quote-${index}`)}
        </blockquote>
      )
      index += 1
      continue
    }

    if (/^[-*]\s+/.test(trimmed)) {
      const items = []
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''))
        index += 1
      }
      if (inColloc && onCollocClick) {
        blocks.push(
          <ul key={`ul-${index}`} className="list-disc space-y-2 pl-5 text-base leading-7 text-app-label-secondary">
            {items.map((item, itemIndex) => (
              <li
                key={`ul-item-${itemIndex}`}
                onClick={withGenericClick(() => {
                  const { sideA, sideB } = parseColloc(item)
                  onCollocClick(sideA, sideB)
                })}
                className="-mx-1 cursor-pointer rounded px-1 underline decoration-dashed underline-offset-2 transition-colors hover:bg-app-fill-secondary"
              >
                {parseInline(item, `ul-${itemIndex}`)}
              </li>
            ))}
          </ul>
        )
        inColloc = false
      } else {
        blocks.push(
          <ul key={`ul-${index}`} className="list-disc space-y-2 pl-5 text-base leading-7 text-app-label-secondary">
            {items.map((item, itemIndex) => (
              <li key={`ul-item-${itemIndex}`}>{parseInline(item, `ul-${itemIndex}`)}</li>
            ))}
          </ul>
        )
        inColloc = false
      }
      continue
    }

    if (/^\d+\.\s+/.test(trimmed)) {
      const items = []
      while (index < lines.length && /^\d+\.\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^\d+\.\s+/, ''))
        index += 1
      }
      if (inColloc && onCollocClick) {
        blocks.push(
          <ol key={`ol-${index}`} className="list-decimal space-y-2 pl-5 text-base leading-7 text-app-label-secondary">
            {items.map((item, itemIndex) => (
              <li
                key={`ol-item-${itemIndex}`}
                onClick={withGenericClick(() => {
                  const { sideA, sideB } = parseColloc(item)
                  onCollocClick(sideA, sideB)
                })}
                className="-mx-1 cursor-pointer rounded px-1 underline decoration-dashed underline-offset-2 transition-colors hover:bg-app-fill-secondary"
              >
                {parseInline(item, `ol-${itemIndex}`)}
              </li>
            ))}
          </ol>
        )
        inColloc = false
      } else {
        blocks.push(
          <ol key={`ol-${index}`} className="list-decimal space-y-2 pl-5 text-base leading-7 text-app-label-secondary">
            {items.map((item, itemIndex) => (
              <li key={`ol-item-${itemIndex}`}>{parseInline(item, `ol-${itemIndex}`)}</li>
            ))}
          </ol>
        )
      }
      continue
    }

    const paragraph = []
    while (index < lines.length && lines[index].trim()) {
      const current = lines[index].trim()
      if (/^(#{1,3})\s+/.test(current) || /^[-*]\s+/.test(current) || /^\d+\.\s+/.test(current) || current.startsWith('```') || current.startsWith('>')) {
        break
      }
      paragraph.push(current)
      index += 1
      if (current.includes('💡 常见搭配')) break
    }
    const isCollocHeader = paragraph.some(l => l.includes('💡 常见搭配'))
    if (inColloc && onCollocClick && !isCollocHeader) {
      blocks.push(
        <ul key={`p-${index}`} className="list-disc space-y-2 pl-5 text-base leading-7 text-app-label-secondary">
          {paragraph.map((line, lineIndex) => (
            <li
              key={`p-item-${lineIndex}`}
              onClick={withGenericClick(() => {
                const { sideA, sideB } = parseColloc(line)
                onCollocClick(sideA, sideB)
              })}
              className="-mx-1 cursor-pointer rounded px-1 underline decoration-dashed underline-offset-2 transition-colors hover:bg-app-fill-secondary"
            >
              {parseInline(line, `p-${index}-${lineIndex}`)}
            </li>
          ))}
        </ul>
      )
      inColloc = false
    } else {
      blocks.push(
        <p key={`p-${index}`} className="text-base leading-7 text-app-label-secondary">
          {paragraph.map((line, lineIndex) => (
            <span key={lineIndex}>
              {parseInline(line, `p-${index}-${lineIndex}`)}
              {lineIndex < paragraph.length - 1 && <br />}
            </span>
          ))}
        </p>
      )
      inColloc = isCollocHeader
    }
  }

  return <div className="space-y-4">{blocks}</div>
}
