/**
 * 把常见搭配文本拆成卡片 A 面和 B 面，优先保留英文表达在 A 面、中文解释在 B 面。
 */
export function parseColloc(text) {
  const trimmed = text.trim()
  const bracketMatch = trimmed.match(/^(.*)[（([【「]([^（）()[\]【】「」]+)[）)\]】」]\s*$/)
  if (bracketMatch) {
    return { sideA: bracketMatch[1].trim(), sideB: bracketMatch[2].trim() }
  }

  const separatorMatch = findReadableSeparator(trimmed)
  if (!separatorMatch) return { sideA: trimmed, sideB: '' }

  return {
    sideA: trimmed.slice(0, separatorMatch.index).trim(),
    sideB: trimmed.slice(separatorMatch.index + separatorMatch.separator.length).trim(),
  }
}

/**
 * 找到英文搭配和中文解释之间的可读分隔符，避免把纯英文逗号句拆成卡片双面。
 */
function findReadableSeparator(text) {
  const separators = ['：', ':', '，', ',']
  let match = null

  for (const separator of separators) {
    let index = text.indexOf(separator)
    while (index !== -1) {
      const sideA = text.slice(0, index).trim()
      const sideB = text.slice(index + separator.length).trim()
      if (/[A-Za-z]/.test(sideA) && /[\u4e00-\u9fff]/.test(sideB)) {
        match = { index, separator }
      }
      index = text.indexOf(separator, index + separator.length)
    }
    if (match) return match
  }

  return null
}
