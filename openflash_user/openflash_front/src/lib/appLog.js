/** 统一前端日志工具，自动在消息前注入 [E:code]，确保所有客户端错误日志可按 code 溯源。 */

export function appError(code, msg, ...args) {
  console.error(`[E:${code}] ${msg}`, ...args)
}

export function appWarn(code, msg, ...args) {
  console.warn(`[E:${code}] ${msg}`, ...args)
}
