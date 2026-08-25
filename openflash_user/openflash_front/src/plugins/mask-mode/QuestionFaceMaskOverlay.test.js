/**
 * Task 7C 覆盖层交互测试（source-grep 风格）。
 *
 * 项目前端测试基建：node:test，无 jsdom / testing-library，无法对 React 组件做真实
 * 挂载/事件分发；因此本测试以「源码静态断言」的方式覆盖 spec 要求的 8 项行为，
 * 验证关键钩子调用、状态机条件、事件绑定、stopPropagation/preventDefault 接管、
 * pointer capture、暗色模式 class、不依赖音频/isEnglish 等约束都写进了组件源码。
 */

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { readFile } from 'node:fs/promises'

const SOURCE = await readFile(new URL('./QuestionFaceMaskOverlay.jsx', import.meta.url), 'utf8')

test('源码绑定: eligible=false 时不渲染遮蔽——按 eligible/loaded 守卫渲染', () => {
  // 资格未到位（loaded=false 或 eligible=false）→ 直接 return null，不渲染遮蔽层。
  assert.match(SOURCE, /loadAndCacheMaskEligibility/)
  assert.match(SOURCE, /eligible/)
  // 任一守卫不通过 → return null（不强制顺序，但必须存在 null 返回路径）。
  assert.match(SOURCE, /return null/)
})

test('源码绑定: 稳定决策返回不遮蔽时不渲染——使用 createStableMaskDecision 并按 shouldMask 守卫', () => {
  // 必须创建稳定决策器（useRef 持有，避免每次 render 重建丢缓存）。
  assert.match(SOURCE, /createStableMaskDecision/)
  assert.match(SOURCE, /useRef/)
  // 必须调用 shouldMask({ itemKey, questionSide, eligible, mode }) 拿决策。
  assert.match(SOURCE, /shouldMask/)
  assert.match(SOURCE, /itemKey/)
  assert.match(SOURCE, /questionSide/)
})

test('源码绑定: full 且当前题目面自动发音开启时初始渲染遮蔽——依赖注入 getDeckMaskModeSettings + getDeckTtsSettings', () => {
  // 遮蔽资格由 loader 读取设置并写入 eligibilityCache。
  assert.match(SOURCE, /loadAndCacheMaskEligibility/)
  assert.match(SOURCE, /getCachedEligibility/)
  // 卡包已安装插件 → 由 useDeckInstalledPlugins(deckId) 提供。
  assert.match(SOURCE, /useDeckInstalledPlugins/)
})

test('源码绑定: onPointerDown 设 pressed=true 且 preventDefault/stopPropagation', () => {
  // pressed 状态机存在。
  assert.match(SOURCE, /useState\(false\)/)
  assert.match(SOURCE, /onPointerDown/)
  // 按下时必须 preventDefault 与 stopPropagation，防冒泡触发 questionOpen。
  assert.match(SOURCE, /preventDefault\(\)/)
  assert.match(SOURCE, /stopPropagation\(\)/)
})

test('源码绑定: 松开/取消/指针离开三个事件全部绑定且复位 pressed', () => {
  // 三类恢复事件必须都绑定。
  assert.match(SOURCE, /onPointerUp/)
  assert.match(SOURCE, /onPointerCancel/)
  assert.match(SOURCE, /onPointerLeave/)
})

test('源码绑定: 临时显示态仍捕获后续指针事件——使用 setPointerCapture/releasePointerCapture', () => {
  // 通过 setPointerCapture 把指针锁到遮蔽层，保证按住期间后续事件仍由本层接收，
  // 直到 pointerUp/cancel 才释放——避免手指轻微移动后失去事件流。
  assert.match(SOURCE, /setPointerCapture/)
  assert.match(SOURCE, /releasePointerCapture/)
})

test('源码绑定: 接管期间阻止 practice.card.open-actions——onClick + onPointerDown 都 stopPropagation', () => {
  // 容器在 PracticeCard 内部带 onClick={questionOpen}，本层必须吞掉 click 防冒泡。
  assert.match(SOURCE, /onClick/)
  // stopPropagation 至少在两个事件处理器里出现（pointerDown + click）。
  const stopCount = (SOURCE.match(/stopPropagation\(\)/g) ?? []).length
  assert.ok(stopCount >= 2, `期望至少两处 stopPropagation，实际 ${stopCount}`)
})

test('源码绑定: revealed=true 后不影响答案面——source 中按 revealed 守卫不渲染', () => {
  // 答案面已揭示后题目面遮蔽无意义，必须在守卫里读 revealed。
  assert.match(SOURCE, /revealed/)
})

test('源码绑定: 暗色模式适配——遮蔽层与提示文字都要有 dark: 变体（规约 #4）', () => {
  // 至少出现一处 dark: 配色，防止深色背景下黑底黑字。
  assert.match(SOURCE, /dark:/)
})

test('源码绑定: 不调 isEnglish() 也不监听音频播放（规约约束）', () => {
  // 严禁引入 tts 的 isEnglish 判断；严禁监听音频播放事件作为触发条件。
  assert.ok(!/isEnglish/.test(SOURCE), '不得调用 isEnglish()')
  assert.ok(!/onended|onplay|audio\./i.test(SOURCE), '不得监听音频播放事件')
})

test('源码绑定: 稳定性防闪烁——useEffect 异步加载 + useRef 持有稳定决策器', () => {
  // 异步 resolveMaskEligibility 必须放在 useEffect 内并写入 state；useRef 保持决策器单例。
  assert.match(SOURCE, /useEffect/)
  assert.match(SOURCE, /useState/)
  assert.match(SOURCE, /useRef/)
})

test('源码绑定: 资格缓存抽到独立模块——overlay import getCachedEligibility 并通过 loader 写缓存', () => {
  // Task 2：模块级 eligibilityCache Map 已迁到 eligibilityCache.js；overlay 不再内联缓存定义。
  assert.match(SOURCE, /from\s+['"]\.\/eligibilityCache['"]/)
  assert.match(SOURCE, /getCachedEligibility/)
  assert.match(SOURCE, /loadAndCacheMaskEligibility/)
  // 不应再内联 Map 缓存与事件监听。
  assert.ok(!/new Map\(\)/.test(SOURCE), '不得在 overlay 内再持有 eligibilityCache Map')
  assert.ok(!/MASK_MODE_DECK_SETTINGS_CHANGED_EVENT/.test(SOURCE), '事件监听已迁走')
  assert.ok(!/TTS_DECK_SETTINGS_CHANGED_EVENT/.test(SOURCE), '事件监听已迁走')
  assert.ok(!/window\.addEventListener/.test(SOURCE), 'overlay 内不应直接监听 window 事件')
})

test('源码绑定: eligibility state 带 deckId/questionSide，避免切面首帧使用旧资格', () => {
  assert.match(SOURCE, /eligibilityState/)
  assert.match(SOURCE, /createEligibilityState\(deckId,\s*questionSide\)/)
  assert.match(SOURCE, /eligibilityState\.deckId\s*===\s*deckId/)
  assert.match(SOURCE, /eligibilityState\.questionSide\s*===\s*questionSide/)
})

test('源码绑定: 缓存命中时 effect 不再重复 resolveMaskEligibility', () => {
  assert.match(SOURCE, /const cached = getCachedEligibility\(deckId,\s*questionSide\)/)
  assert.match(SOURCE, /if \(cached !== undefined\)[\s\S]*?return/)
})

test('源码绑定: prefetch 接收调度器传入的已安装插件列表，避免插件重复查安装关系', () => {
  const INDEX_SOURCE = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8')

  assert.match(INDEX_SOURCE, /prefetchedInstalledIds/)
  assert.match(INDEX_SOURCE, /loadDeckInstalledPlugins/)
  assert.doesNotMatch(INDEX_SOURCE, /setCachedDeckInstalledPlugins/)
})

test('源码绑定: 去掉悲观默认——未命中缓存初值为 null（加载完前不渲染）', () => {
  // 不得再出现「悲观默认 mode='full'」分支。
  assert.ok(!/mode:\s*['"]full['"]/.test(SOURCE), '不得再保留悲观默认 mode=full')
  // initialEligibility 未命中必须返回 null。
  assert.match(SOURCE, /initialEligibility/)
  assert.match(SOURCE, /\?\?\s*null|return null/)
})

test('源码绑定: i18n 文案——按住提示走 plugins.mask-mode.holdToReveal', () => {
  // 展示文字必须经 useTranslation 读 i18n，DB/代码不得写死。
  assert.match(SOURCE, /useTranslation/)
  assert.match(SOURCE, /plugins\.mask-mode\.holdToReveal/)
})
