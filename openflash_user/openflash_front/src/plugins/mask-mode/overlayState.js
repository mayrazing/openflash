/**
 * 推导覆盖层渲染状态。纯函数，无副作用。
 * @param {object} args
 * @param {boolean} args.revealed - 卡片是否已翻面
 * @param {boolean} args.eligibilityLoaded - 资格判定是否完成
 * @param {boolean} args.eligible - 是否符合遮蔽条件
 * @param {boolean} args.shouldMask - 决策是否需要遮蔽
 * @param {boolean} args.pressed - 用户当前是否按下覆盖层
 * @returns {'hidden' | 'masked' | 'transparent'}
 *   hidden: 不渲染遮蔽层（revealed/未加载/不合资格/决策不遮）
 *   masked: 渲染遮蔽层且视觉遮住（未按下）
 *   transparent: 渲染遮蔽层但透明（按下临时显示原题，仍捕获 pointer）
 */
export function computeOverlayState({ revealed, eligibilityLoaded, eligible, shouldMask, pressed }) {
  if (revealed) return 'hidden'
  if (!eligibilityLoaded) return 'hidden'
  if (!eligible || !shouldMask) return 'hidden'
  if (pressed) return 'transparent'
  return 'masked'
}
