import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveMaskEligibility, resolveMaskEligibilityFromSettings } from './maskEligibility.js'

// 纯函数资格判断测试：不依赖 React/DOM/网络，getter 全部注入打桩。
// questionSide 取值对齐项目约定：小写 'a' / 'b'。
// installedIds 为数组，用 includes('tts') 判断安装。
// TTS 自动发音字段：autoSpeakA / autoSpeakB（见 plugins/tts/api.js DEFAULT_DECK_TTS_SETTINGS）。

test('tts 未安装时返回 eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: [],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: true }),
  })
  assert.equal(result.eligible, false)
})

test('tts 未安装时不读 TTS 设置（getter 不应被调用）', async () => {
  let called = false
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['other-plugin'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => { called = true; return { autoSpeakA: true } },
  })
  assert.equal(result.eligible, false)
  assert.equal(called, false)
})

test('题目面 A 且 autoSpeakA=true 时返回 eligible=true', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.eligible, true)
  assert.equal(result.mode, 'random')
})

test('题目面 B 且 autoSpeakB=true 时返回 eligible=true', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'b',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: false, autoSpeakB: true }),
  })
  assert.equal(result.eligible, true)
  assert.equal(result.mode, 'random')
})

test('题目面 A 但 autoSpeakA=false 时返回 eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: false, autoSpeakB: true }),
  })
  assert.equal(result.eligible, false)
})

test('题目面 B 但 autoSpeakB=false 时返回 eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'b',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.eligible, false)
})

test('读取遮蔽设置失败时按默认 random 继续决策', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => { throw new Error('mask read boom') },
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  // 回退 random 后继续决策：A 面自动发音开着 → 有资格。
  assert.equal(result.mode, 'random')
  assert.equal(result.eligible, true)
})

test('读取 TTS 设置失败时返回 eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'full' }),
    getDeckTtsSettings: async () => { throw new Error('tts read boom') },
  })
  assert.equal(result.eligible, false)
  // mode 仍来自遮蔽设置，不受 TTS 读取失败影响。
  assert.equal(result.mode, 'full')
})

test('mode=full 时原样透传', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'full' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.eligible, true)
  assert.equal(result.mode, 'full')
})

test('遮蔽设置返回非法 mode 值时回退 random', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'weird-mode' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.mode, 'random')
  assert.equal(result.eligible, true)
})

test('非法 questionSide（非 a/b）时返回 eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'x',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: true }),
  })
  assert.equal(result.eligible, false)
})

test('installedIds 非数组（如 undefined）时视为未安装，eligible=false', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: undefined,
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: true }),
  })
  assert.equal(result.eligible, false)
})

test('遮蔽设置返回 null 时回退 random', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => null,
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.mode, 'random')
  assert.equal(result.eligible, true)
})

test('TTS 设置返回缺字段时该面视为未启用自动发音', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({}),
  })
  assert.equal(result.eligible, false)
})

test('resolveMaskEligibilityFromSettings 用已加载设置计算单面资格，不再读取网络 getter', () => {
  const sideA = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: { mode: 'full' },
    ttsSettings: { autoSpeakA: true, autoSpeakB: false },
  })
  const sideB = resolveMaskEligibilityFromSettings({
    questionSide: 'b',
    installedIds: ['tts'],
    maskSettings: { mode: 'full' },
    ttsSettings: { autoSpeakA: true, autoSpeakB: false },
  })

  assert.deepEqual(sideA, { eligible: true, mode: 'full' })
  assert.deepEqual(sideB, { eligible: false, mode: 'full' })
})

test('resolveMaskEligibilityFromSettings 在 tts 未安装时不需要 TTS 设置', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['mask-mode'],
    maskSettings: { mode: 'weird' },
    ttsSettings: { autoSpeakA: true },
  })

  assert.deepEqual(result, { eligible: false, mode: 'random' })
})

test('enabled=false 时（同步版）返回 eligible=false，mode 保留', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: { mode: 'full', enabled: false },
    ttsSettings: { autoSpeakA: true, autoSpeakB: true },
  })
  assert.deepEqual(result, { eligible: false, mode: 'full' })
})

test('enabled=false 时（同步版）不读 ttsSettings 也能短路', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: { mode: 'random', enabled: false },
    ttsSettings: null,
  })
  assert.deepEqual(result, { eligible: false, mode: 'random' })
})

test('enabled=true 时（同步版）走 tts.autoSpeak 原逻辑', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: { mode: 'full', enabled: true },
    ttsSettings: { autoSpeakA: true, autoSpeakB: false },
  })
  assert.deepEqual(result, { eligible: true, mode: 'full' })
})

test('enabled 缺失（同步版）按 true 对待，走原 autoSpeak 逻辑', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: { mode: 'random' },
    ttsSettings: { autoSpeakA: true, autoSpeakB: false },
  })
  assert.deepEqual(result, { eligible: true, mode: 'random' })
})

test('maskSettings=null 时（同步版）enabled 不误判为关闭', () => {
  const result = resolveMaskEligibilityFromSettings({
    questionSide: 'a',
    installedIds: ['tts'],
    maskSettings: null,
    ttsSettings: { autoSpeakA: true, autoSpeakB: false },
  })
  // maskSettings 为空仍走 autoSpeak，不被 enabled===false 短路
  assert.deepEqual(result, { eligible: true, mode: 'random' })
})

test('enabled=false 时（异步版）短路，不读 tts getter', async () => {
  let ttsCalled = false
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'full', enabled: false }),
    getDeckTtsSettings: async () => { ttsCalled = true; return { autoSpeakA: true } },
  })
  assert.equal(result.eligible, false)
  assert.equal(result.mode, 'full')
  assert.equal(ttsCalled, false)
})

test('enabled=true 时（异步版）走 autoSpeak 原逻辑', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'b',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random', enabled: true }),
    getDeckTtsSettings: async () => ({ autoSpeakA: false, autoSpeakB: true }),
  })
  assert.equal(result.eligible, true)
  assert.equal(result.mode, 'random')
})

test('enabled 缺失（异步版）按 true 对待', async () => {
  const result = await resolveMaskEligibility({
    deckId: 'd1',
    questionSide: 'a',
    installedIds: ['tts'],
    getDeckMaskModeSettings: async () => ({ mode: 'random' }),
    getDeckTtsSettings: async () => ({ autoSpeakA: true, autoSpeakB: false }),
  })
  assert.equal(result.eligible, true)
})
