/**
 * TTS 纯工具函数，无 browser/Vite 依赖，可在 Node 测试环境直接运行。
 */

/**
 * 规范化 TTS 文本：去首尾空格、展开斜杠为 or、合并多余空格。
 * @param {string} text
 * @returns {string}
 */
export function normalizeTtsText(text) {
    if (!text) return ''
    return text.trim().replaceAll('/', ' or ').replace(/\s+/g, ' ').trim()
}

/**
 * 判断文本是否为英文。
 * 规则：必须包含英文 ASCII 字母；允许数字和符号；出现其他语言字母或组合音标则视为非英文。
 * @param {string} text
 * @returns {boolean}
 */
export function isEnglish(text) {
    const normalized = normalizeTtsText(text)
    if (!normalized) return false

    let hasAsciiLetter = false
    for (const char of normalized) {
        if (/\s/u.test(char)) continue
        if (/[a-zA-Z]/.test(char)) {
            hasAsciiLetter = true
            continue
        }
        if (/\p{Letter}|\p{Mark}/u.test(char)) {
            return false
        }
    }
    return hasAsciiLetter
}
