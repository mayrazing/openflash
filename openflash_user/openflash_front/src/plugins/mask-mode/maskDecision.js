/**
 * 稳定初始遮蔽决策（纯函数模块）。
 *
 * 职责：在 mask-mode 的 random 模式下，以 `itemKey + questionSide` 为 key
 * 稳定缓存本题的初始遮蔽决策，避免每次 render 重新随机导致遮蔽闪烁。
 *
 * 纯函数约束：
 * - 不调 React hook、不读 DOM、不读网络。
 * - random 函数由调用方注入（默认 Math.random，测试注入确定性序列验证稳定性）。
 * - random 只决定「整面是否遮蔽」这一个 boolean，不决定遮蔽比例/部分内容。
 *
 * 取值约定（对齐 maskEligibility.js）：
 * - questionSide：'a' / 'b'。
 * - mode：'random' | 'full'，非法值回退 random。
 *
 * 决策规则：
 * - eligible !== true → 永不遮蔽（不写缓存，后续 eligible 变 true 仍可重新决策）。
 * - mode === 'full' 且 eligible=true → 总是遮蔽（不调 random，不写缓存）。
 * - 否则按 random 决策：首次调注入 random()，结果 < RANDOM_MASK_THRESHOLD 视为遮蔽，
 *   以 `${itemKey}:${questionSide}` 为 key 缓存，后续命中缓存直接返回。
 */

/** random 低于该阈值则整面遮蔽（0.5 → 约一半的题初始被遮蔽）。 */
const RANDOM_MASK_THRESHOLD = 0.5

/**
 * 创建一个稳定的遮蔽决策器。
 *
 * @param {object} [opts]
 * @param {() => number} [opts.random=Math.random] - 注入的随机数生成函数，返回 [0,1)。
 * @returns {{ shouldMask: (params: { itemKey: string, questionSide: string, eligible: boolean, mode: string }) => boolean }}
 */
export function createStableMaskDecision({ random = Math.random } = {}) {
  // 按 `${itemKey}:${questionSide}` 缓存首次 random 决策，保证同题多次调用稳定。
  const cache = new Map()

  return {
    /**
     * 判断当前题目面是否应整面遮蔽。
     *
     * @param {object} params
     * @param {string} params.itemKey - 题目稳定标识。
     * @param {string} params.questionSide - 题目面 'a' / 'b'。
     * @param {boolean} params.eligible - 该面是否有遮蔽资格（来自 maskEligibility）。
     * @param {string} params.mode - 遮蔽模式 'random' | 'full'。
     * @returns {boolean} true=整面遮蔽，false=不遮蔽。
     */
    shouldMask({ itemKey, questionSide, eligible, mode }) {
      // 1. 无资格一律不遮蔽，且不写缓存（eligible 后续变 true 时仍可重新决策）。
      if (eligible !== true) {
        return false
      }

      // 2. full 模式总是整面遮蔽，无需 random，也不写缓存。
      if (mode === 'full') {
        return true
      }

      // 3. random 模式（含非法 mode 回退）：以题+面为 key 稳定缓存首次决策。
      const key = `${itemKey}:${questionSide}`
      if (cache.has(key)) {
        return cache.get(key)
      }

      const decision = random() < RANDOM_MASK_THRESHOLD
      cache.set(key, decision)
      return decision
    },
  }
}
