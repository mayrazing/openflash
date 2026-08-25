export const SESSION_INVALIDATION_REASON_STORAGE_KEY = 'openflash.sessionInvalidationReason'

const INVALIDATIONS_BY_CODE = Object.freeze({
  40102: Object.freeze({ code: 40102, reason: 'SESSION_EXPIRED', reasonKey: 'errors.40102' }),
  40103: Object.freeze({ code: 40103, reason: 'BANNED', reasonKey: 'errors.40103' }),
  40104: Object.freeze({ code: 40104, reason: 'DELETED', reasonKey: 'errors.40104' }),
})

const INVALIDATIONS_BY_REASON = Object.freeze(Object.fromEntries(
  Object.values(INVALIDATIONS_BY_CODE).map(invalidation => [invalidation.reason, invalidation]),
))

const subscribers = new Set()
let activeInvalidation = null
let authWindowGeneration = 0

/** 发布当前登录会话的首次失效原因；重复来源不会再次通知或改写原因。 */
export function publishSessionInvalidation(input) {
  const invalidation = normalizeInvalidation(input)
  if (!invalidation || activeInvalidation) return false

  activeInvalidation = invalidation
  authWindowGeneration++
  for (const subscriber of [...subscribers]) subscriber(invalidation)
  return true
}

/** 订阅全局会话失效事件。 */
export function subscribeSessionInvalidation(subscriber) {
  subscribers.add(subscriber)
  return () => subscribers.delete(subscriber)
}

/** 返回当前认证窗口内已发生的首次会话失效。 */
export function getActiveSessionInvalidation() {
  return activeInvalidation
}

/** 返回调用方当前所属的认证窗口 token。 */
export function captureAuthWindowToken() {
  return authWindowGeneration
}

/** 判断异步 continuation 是否仍属于当前有效认证窗口。 */
export function isAuthWindowTokenCurrent(token) {
  return token === authWindowGeneration && !activeInvalidation
}

/** 开启新登录或注册窗口，并让所有旧 continuation 失效。 */
export function beginAuthAttempt() {
  activeInvalidation = null
  return ++authWindowGeneration
}

function normalizeInvalidation(input) {
  const code = typeof input === 'object' ? Number(input?.code) : Number(input)
  if (INVALIDATIONS_BY_CODE[code]) return INVALIDATIONS_BY_CODE[code]

  const reason = typeof input === 'object' ? input?.reason : input
  return INVALIDATIONS_BY_REASON[reason] ?? null
}
