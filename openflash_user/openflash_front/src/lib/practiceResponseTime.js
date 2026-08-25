/**
 * 反应时间阈值（毫秒）。
 * 前端在从后端拿到配置前使用此值作为兜底。
 */
export const DEFAULT_THRESHOLDS = {
  timeoutMs: 60_000,
  grade3SlowMs: 8_000,
  grade2SlowMs: 15_000,
}

/**
 * 根据反应时间将用户评分降档。
 * - originalRating 3 且 responseTimeMs > grade3SlowMs → 降为 2
 * - originalRating 2 且 responseTimeMs > grade2SlowMs → 降为 1
 * - 其余不变
 *
 * @param {number} originalRating - 用户按下的评分 (0-3)
 * @param {number|null} responseTimeMs - 翻牌到按钮的毫秒数，null 表示无法计时
 * @param {object} thresholds - { grade3SlowMs, grade2SlowMs }
 * @returns {number} 实际送入 FSRS 的评分
 */
export function computeAppliedRating(originalRating, responseTimeMs, thresholds = DEFAULT_THRESHOLDS) {
  if (responseTimeMs === null || responseTimeMs === undefined) return originalRating
  if (originalRating === 3 && responseTimeMs > thresholds.grade3SlowMs) return 2
  if (originalRating === 2 && responseTimeMs > thresholds.grade2SlowMs) return 1
  return originalRating
}

/**
 * 判断反应时间是否超时（应当作废本次评分）。
 *
 * @param {number|null} responseTimeMs
 * @param {number} timeoutMs
 * @returns {boolean}
 */
export function isTimedOut(responseTimeMs, timeoutMs) {
  if (responseTimeMs === null || responseTimeMs === undefined) return false
  return responseTimeMs > timeoutMs
}
