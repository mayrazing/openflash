import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useDeckInstalledPlugins from '../useDeckInstalledPlugins'
import { createStableMaskDecision } from './maskDecision'
import { computeOverlayState } from './overlayState'
import { getCachedEligibility } from './eligibilityCache'
import { loadAndCacheMaskEligibility } from './eligibilityLoader'


/**
 * 题目面遮蔽覆盖层（Task 7C）。
 *
 * 职责：当卡包安装了 mask-mode、tts，且当前题目面启用了自动发音时，
 * 在 PracticeCard 题目面之上渲染一个遮蔽层；用户按住时临时显示原题，
 * 松开/取消/离开后恢复遮蔽。接管期间阻止 click/pointer 冒泡，防止
 * 触发 practice.card.open-actions（核心 questionOpen 动作）。
 *
 * 不做的事：
 * - 不按文本语言判断（mask 由 autoSpeakA/B 静态开关决定）。
 * - 不监听音频播放事件（mask 不等音频结果）。
 *
 * 防闪烁要点：
 * - 资格缓存抽到 eligibilityCache 模块；prefetch 在进入练习前预热写入，
 *   overlay 同步读取，避免「先看到字、再看到遮罩」的网络往返延迟。
 * - 未命中缓存时初值返 null（Task 7C 原版「加载完前不渲染」语义），
 *   prefetch 已保证首次进入前命中，未命中是异常路径，回退到不遮蔽避免误盖。
 * - 设置变更事件（mask/tts）由 eligibilityCache 模块负责清缓存，本组件无需关心。
 * - 稳定决策器用 useRef 持有，跨 render 复用 random 缓存。
 */

/**
 * 初始 eligibility 值：同 deckId+side 已缓存就同步返回，否则返 null（加载完前不渲染）。
 * 缓存来自 eligibilityCache 模块——prefetch 阶段已经写入；缓存未命中是异常分支。
 */
function initialEligibility(deckId, questionSide) {
  if (deckId == null) return null
  return getCachedEligibility(deckId, questionSide) ?? null
}

function createEligibilityState(deckId, questionSide, value = initialEligibility(deckId, questionSide)) {
  return { deckId, questionSide, value }
}

export default function QuestionFaceMaskOverlay({
  questionSide,
  revealed,
  itemKey,
  deckId,
}) {
  const { t } = useTranslation()

  // 卡包已安装插件 id 列表（由 useDeckInstalledPlugins 提供 loaded 标记）。
  const { installedIds, loaded: installedLoaded } = useDeckInstalledPlugins(deckId)

  // 资格状态：用 useState 函数式初始化，命中缓存立即同步；未命中走 null（不渲染遮罩）。
  const [eligibilityState, setEligibilityState] = useState(() => createEligibilityState(deckId, questionSide))

  // 按住状态：true=临时显示原题（遮蔽层视觉上透明但仍捕获 pointer）。
  const [pressed, setPressed] = useState(false)

  // 稳定决策器，useRef 持有单例，跨 render 保留 random 缓存。
  const decisionRef = useRef(null)
  if (decisionRef.current === null) {
    decisionRef.current = createStableMaskDecision()
  }

  // 异步加载真实 eligibility 并写缓存：依赖 deckId/questionSide/installedIds，变更则重算。
  useEffect(() => {
    if (!deckId || !installedLoaded) {
      return
    }
    const cached = getCachedEligibility(deckId, questionSide)
    if (cached !== undefined) {
      setEligibilityState(createEligibilityState(deckId, questionSide, cached))
      return
    }
    let cancelled = false
    loadAndCacheMaskEligibility({
      deckId,
      questionSide,
      installedIds,
    })
      .then((result) => {
        if (cancelled) return
        setEligibilityState(createEligibilityState(deckId, questionSide, result))
      })
      .catch(() => {
        if (cancelled) return
        setEligibilityState(createEligibilityState(deckId, questionSide, { eligible: false, mode: 'random' }))
      })
    return () => { cancelled = true }
  }, [deckId, questionSide, installedLoaded, installedIds])

  // deckId/questionSide 切换时立即用缓存刷新当前 state，避免显示别题的旧 eligibility。
  useEffect(() => {
    setEligibilityState(createEligibilityState(deckId, questionSide))
  }, [deckId, questionSide])

  // 题目切换时复位按住状态，避免上一题的 pressed 残留到新题。
  useEffect(() => {
    setPressed(false)
  }, [itemKey, questionSide])

  const eligibility = eligibilityState.deckId === deckId && eligibilityState.questionSide === questionSide
    ? eligibilityState.value
    : initialEligibility(deckId, questionSide)

  // 守卫：未加载完、答案已揭示、资格不到位，一律不渲染遮蔽。
  // 稳定决策：同题同面只随机一次，后续命中缓存——避免 render 闪烁。
  // 注意 shouldMask 必须在 eligibility 加载完成后才有意义；未加载时传 false 占位。
  const shouldMask = eligibility !== null && eligibility.eligible
    ? decisionRef.current.shouldMask({
        itemKey,
        questionSide,
        eligible: eligibility.eligible,
        mode: eligibility.mode,
      })
    : false

  // 把所有守卫推导集中到纯函数 computeOverlayState，便于单测覆盖真值表。
  const overlayState = computeOverlayState({
    revealed,
    eligibilityLoaded: eligibility !== null,
    eligible: eligibility?.eligible ?? false,
    shouldMask,
    pressed,
  })
  if (overlayState === 'hidden') return null

  /** 按下：锁定指针 + 阻止冒泡，临时显示原题。 */
  const handlePointerDown = (event) => {
    event.preventDefault()
    event.stopPropagation()
    try { event.currentTarget.setPointerCapture(event.pointerId) } catch { /* 老浏览器降级 */ }
    setPressed(true)
  }

  /** 松开/取消/离开：释放指针并恢复遮蔽。 */
  const handlePointerEnd = (event) => {
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* 降级 */ }
    setPressed(false)
  }

  /** 吞掉 click，防止冒泡触发父容器 onClick={questionOpen}（practice.card.open-actions）。 */
  const handleClick = (event) => {
    event.preventDefault()
    event.stopPropagation()
  }

  // 视觉：transparent 态完全透明（让原题显出来）但仍占据 inset-0 接收 pointer；
  // masked 态深灰底+提示文案，完全不透明（无 /95 透明度），暗色模式适配。
  // pointer-events-auto：PluginSlot 外层是 pointer-events-none，本组件要接管 pointer
  // 才能阻止题目面 onClick 被冒泡触发；不接管态（hidden）直接返 null，不阻挡父容器点击。
  // touch-manipulation：禁用双击缩放等手势但保留滚动/拖动，不阻断卡片外区域可能的滚动手势
  //（touch-none 会完全禁用触摸手势，可能误伤滚动）。
  const overlayClass = [
    'pointer-events-auto absolute inset-0 z-10 flex items-center justify-center rounded-xl select-none touch-manipulation cursor-pointer transition-opacity',
    overlayState === 'transparent'
      ? 'bg-transparent dark:bg-transparent opacity-0'
      : 'bg-app-surface-tertiary opacity-100',
  ].join(' ')

  return (
    <div
      role="button"
      aria-label={t('plugins.mask-mode.holdToReveal')}
      className={overlayClass}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerEnd}
      onPointerCancel={handlePointerEnd}
      onPointerLeave={handlePointerEnd}
      onClick={handleClick}
    >
      {overlayState === 'masked' && (
        <span className="text-sm font-medium text-app-label-secondary">
          {t('plugins.mask-mode.holdToReveal')}
        </span>
      )}
    </div>
  )
}
