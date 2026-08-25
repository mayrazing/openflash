import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const indexCss = readFileSync(new URL('./index.css', import.meta.url), 'utf8')

const appleColors = {
  light: {
    black: '#000000', white: '#ffffff',
    red: '#ff383c', orange: '#ff8d28', green: '#34c759', blue: '#0088ff', purple: '#cb30e0',
    gray: '#8e8e93', gray2: '#aeaeb2', gray3: '#c7c7cc', gray4: '#d1d1d6', gray5: '#e5e5ea', gray6: '#f2f2f7',
  },
  dark: {
    black: '#000000', white: '#ffffff',
    red: '#ff4245', orange: '#ff9230', green: '#30d158', blue: '#0091ff', purple: '#db34f2',
    gray: '#8e8e93', gray2: '#636366', gray3: '#48484a', gray4: '#3a3a3c', gray5: '#2c2c2e', gray6: '#1c1c1e',
  },
  lightContrast: {
    black: '#000000', white: '#ffffff',
    red: '#e9152d', orange: '#c55300', green: '#008932', blue: '#1e6ef4', purple: '#b02fc2',
    gray: '#6c6c70', gray2: '#8e8e93', gray3: '#aeaeb2', gray4: '#bcbcc0', gray5: '#d8d8dc', gray6: '#ebebf0',
  },
  darkContrast: {
    black: '#000000', white: '#ffffff',
    red: '#ff6165', orange: '#ffa056', green: '#4ad968', blue: '#5cb8ff', purple: '#ea8dff',
    gray: '#aeaeb2', gray2: '#7c7c80', gray3: '#545456', gray4: '#444446', gray5: '#363638', gray6: '#242426',
  },
}

const sharedColorMapping = {
  background: 'gray6', surfaceSecondary: 'gray6', fillSecondary: 'gray5', separator: 'gray3',
  secondary: 'gray', tertiary: 'gray2', control: 'gray',
  accent: 'blue', accentFill: 'blue', accentHover: 'blue', accentPressed: 'blue',
  disabledFill: 'gray5', disabledLabel: 'gray',
  danger: 'red', dangerFill: 'red', dangerHover: 'red', dangerPressed: 'red',
  warning: 'orange', warningFill: 'orange', warningHover: 'orange', warningPressed: 'orange',
  success: 'green', successFill: 'green', successHover: 'green', successPressed: 'green',
  familiar: 'purple', onFill: 'white', codeSurface: 'black', codeLabel: 'white',
  selectedBorder: 'blue', focus: 'blue',
}

const appearanceMapping = {
  light: { primary: 'black', surface: 'white', surfaceTertiary: 'gray5', tonal: 'gray6', tonalPressed: 'gray5' },
  dark: { primary: 'white', surface: 'gray5', surfaceTertiary: 'gray4', tonal: 'gray6', tonalPressed: 'gray5' },
  lightContrast: { primary: 'black', surface: 'white', surfaceTertiary: 'gray5', tonal: 'gray6', tonalPressed: 'gray5' },
  darkContrast: { primary: 'white', surface: 'gray5', surfaceTertiary: 'gray4', tonal: 'gray6', tonalPressed: 'gray5' },
}

const effectColors = {
  light: { overlay: 'rgba(0, 0, 0, 0.4)', elevationShadow: 'rgba(0, 0, 0, 0.22)' },
  dark: { overlay: 'rgba(0, 0, 0, 0.55)', elevationShadow: 'rgba(0, 0, 0, 0.64)' },
  lightContrast: { overlay: 'rgba(0, 0, 0, 0.55)', elevationShadow: 'rgba(0, 0, 0, 0.32)' },
  darkContrast: { overlay: 'rgba(0, 0, 0, 0.7)', elevationShadow: 'rgba(0, 0, 0, 0.8)' },
}

const palettes = Object.fromEntries(Object.entries(appleColors).map(([mode, colors]) => {
  const mapping = { ...sharedColorMapping, ...appearanceMapping[mode] }
  const palette = Object.fromEntries(Object.entries(mapping).map(([key, colorName]) => [key, colors[colorName]]))
  const tonal = colors[appearanceMapping[mode].tonal]
  const tonalPressed = colors[appearanceMapping[mode].tonalPressed]
  return [mode, {
    ...palette,
    ...effectColors[mode],
    dangerTonal: tonal, warningTonal: tonal, successTonal: tonal, selectedFill: tonal,
    onDanger: colors.white, onWarning: colors.white, onSuccess: colors.white,
    againTonal: tonal, hardTonal: tonal, goodTonal: tonal, easyTonal: tonal,
    againTonalPressed: tonalPressed, hardTonalPressed: tonalPressed,
    goodTonalPressed: tonalPressed, easyTonalPressed: tonalPressed,
  }]
}))

const requiredTokens = [
  '--app-background', '--app-surface-primary', '--app-surface-secondary', '--app-surface-tertiary', '--app-fill-secondary', '--app-overlay', '--app-elevation-shadow',
  '--app-label-primary', '--app-label-secondary', '--app-label-tertiary', '--app-separator',
  '--app-control-border', '--app-accent-label', '--app-accent-fill', '--app-on-accent',
  '--app-accent-fill-hover', '--app-accent-fill-pressed', '--app-disabled-fill', '--app-disabled-label',
  '--app-danger-label', '--app-danger-fill', '--app-danger-fill-hover', '--app-danger-fill-pressed', '--app-danger-tonal', '--app-on-danger',
  '--app-warning-label', '--app-warning-fill', '--app-warning-fill-hover', '--app-warning-fill-pressed', '--app-warning-tonal', '--app-on-warning',
  '--app-success-label', '--app-success-fill', '--app-success-fill-hover', '--app-success-fill-pressed', '--app-success-tonal', '--app-on-success',
  '--app-practice-again', '--app-practice-again-tonal', '--app-practice-again-tonal-pressed',
  '--app-practice-hard', '--app-practice-hard-tonal', '--app-practice-hard-tonal-pressed',
  '--app-practice-good', '--app-practice-good-tonal', '--app-practice-good-tonal-pressed',
  '--app-practice-easy', '--app-practice-easy-tonal', '--app-practice-easy-tonal-pressed',
  '--app-due-overdue', '--app-due-today', '--app-due-tomorrow', '--app-familiar-base', '--app-familiar-label',
  '--app-selected-fill', '--app-selected-border', '--app-focus', '--app-code-surface', '--app-code-label',
]

const bindings = {
  background: '--app-background', surface: '--app-surface-primary',
  surfaceSecondary: '--app-surface-secondary', surfaceTertiary: '--app-surface-tertiary',
  fillSecondary: '--app-fill-secondary', overlay: '--app-overlay', elevationShadow: '--app-elevation-shadow',
  separator: '--app-separator', primary: '--app-label-primary', secondary: '--app-label-secondary',
  tertiary: '--app-label-tertiary', control: '--app-control-border', accent: '--app-accent-label',
  accentFill: '--app-accent-fill', accentHover: '--app-accent-fill-hover',
  accentPressed: '--app-accent-fill-pressed', disabledFill: '--app-disabled-fill',
  disabledLabel: '--app-disabled-label', danger: '--app-danger-label', dangerFill: '--app-danger-fill',
  dangerHover: '--app-danger-fill-hover', dangerPressed: '--app-danger-fill-pressed', dangerTonal: '--app-danger-tonal', onDanger: '--app-on-danger',
  warning: '--app-warning-label', warningFill: '--app-warning-fill', warningHover: '--app-warning-fill-hover',
  warningPressed: '--app-warning-fill-pressed', warningTonal: '--app-warning-tonal', onWarning: '--app-on-warning',
  success: '--app-success-label', successFill: '--app-success-fill',
  successHover: '--app-success-fill-hover', successPressed: '--app-success-fill-pressed', successTonal: '--app-success-tonal', onSuccess: '--app-on-success',
  familiar: '--app-familiar-base', onFill: '--app-on-accent',
  codeSurface: '--app-code-surface', codeLabel: '--app-code-label',
  againTonal: '--app-practice-again-tonal', againTonalPressed: '--app-practice-again-tonal-pressed',
  hardTonal: '--app-practice-hard-tonal', hardTonalPressed: '--app-practice-hard-tonal-pressed',
  goodTonal: '--app-practice-good-tonal', goodTonalPressed: '--app-practice-good-tonal-pressed',
  easyTonal: '--app-practice-easy-tonal', easyTonalPressed: '--app-practice-easy-tonal-pressed',
  selectedFill: '--app-selected-fill', selectedBorder: '--app-selected-border', focus: '--app-focus',
}

const semanticAliases = [
  ['--app-practice-again', 'danger'], ['--app-practice-hard', 'warning'],
  ['--app-practice-good', 'accent'], ['--app-practice-easy', 'success'],
  ['--app-due-overdue', 'danger'], ['--app-due-today', 'warning'],
  ['--app-due-tomorrow', 'accent'], ['--app-familiar-label', 'familiar'],
]

function declarationsFor(mode) {
  const match = indexCss.match(new RegExp(`/\\* app-theme:${mode}:start \\*/([\\s\\S]*?)/\\* app-theme:${mode}:end \\*/`))
  assert.ok(match, `缺少 app-theme:${mode} 颜色块`)
  return Object.fromEntries([...match[1].matchAll(/(--app-[\w-]+):\s*([^;]+);/g)].map(item => [item[1], item[2].trim()]))
}

function resolveToken(declarations, token, seen = new Set()) {
  assert.ok(!seen.has(token), `${token} 存在循环引用`)
  const value = declarations[token]
  assert.ok(value, `缺少 ${token}`)
  const reference = value.match(/^var\((--app-[\w-]+)\)$/)
  if (!reference) return value.toLowerCase()
  return resolveToken(declarations, reference[1], new Set([...seen, token]))
}

test('语义 token 和四种外观块完整且绑定正确', () => {
  assert.match(indexCss, /--color-app-elevation-shadow:\s*var\(--app-elevation-shadow\);/)
  const validatedTokens = new Set([...Object.values(bindings), ...semanticAliases.map(([token]) => token)])
  for (const token of requiredTokens) assert.ok(validatedTokens.has(token), `${token} 未进入颜色合同`)
  for (const [mode, palette] of Object.entries(palettes)) {
    const declarations = declarationsFor(mode)
    for (const token of requiredTokens) assert.ok(declarations[token], `${mode} 缺少 ${token}`)
    for (const [key, token] of Object.entries(bindings)) {
      assert.equal(resolveToken(declarations, token), palette[key], `${mode} 的 ${token} 绑定错误`)
    }
    for (const [token, key] of semanticAliases) {
      assert.equal(resolveToken(declarations, token), palette[key], `${mode} 的 ${token} 语义别名错误`)
    }
  }
})

test('颜色合同只使用当前外观的 Apple 官方 RGB', () => {
  for (const [mode, palette] of Object.entries(palettes)) {
    const officialColors = new Set(Object.values(appleColors[mode]))
    for (const [key, value] of Object.entries(palette)) {
      if (key === 'overlay' || key === 'elevationShadow') {
        assert.match(value, /^rgba\(0, 0, 0, 0\.\d+\)$/, `${mode} 的 ${key} 必须只使用 Apple Black 基色`)
      } else {
        assert.ok(officialColors.has(value), `${mode} 的 ${key} 不是 Apple 官方 RGB: ${value}`)
      }
    }
  }
})

test('全局颜色源不包含自定义 RGB 或混合色', () => {
  const officialColors = new Set(Object.values(appleColors).flatMap(colors => Object.values(colors)))
  const customHexColors = [...indexCss.matchAll(/#[\da-f]{6}\b/gi)]
    .map(match => match[0].toLowerCase())
    .filter(color => !officialColors.has(color))
  const nonBlackAlphaColors = [...indexCss.matchAll(/rgba\(([^)]+)\)/gi)]
    .map(match => match[0].toLowerCase())
    .filter(color => !/^rgba\(0, 0, 0, 0\.\d+\)$/.test(color))

  assert.deepEqual(customHexColors, [])
  assert.deepEqual(nonBlackAlphaColors, [])
  assert.doesNotMatch(indexCss, /color-mix\(/)
})

test('范围条启用轨道使用控件边框 token', () => {
  const trackBlocks = [
    indexCss.match(/\.range-spring::-webkit-slider-runnable-track\s*\{([\s\S]*?)\}/)?.[1],
    indexCss.match(/\.range-spring::-moz-range-track\s*\{([\s\S]*?)\}/)?.[1],
  ]

  for (const trackBlock of trackBlocks) {
    assert.ok(trackBlock, '缺少范围条启用轨道样式')
    assert.match(trackBlock, /var\(--app-control-border\)/)
    assert.doesNotMatch(trackBlock, /var\(--app-disabled-fill\)/)
  }
})

test('练习选中态边框直接使用选中边框 token', () => {
  const activeBlock = indexCss.match(/\.practice-distribution-card-active\s*\{([\s\S]*?)\}/)?.[1]

  assert.ok(activeBlock, '缺少练习选中态样式')
  assert.match(activeBlock, /border-color:\s*var\(--app-selected-border\)/)
  assert.doesNotMatch(activeBlock, /border-color:\s*color-mix\(/)
})

test('范围条填充段和滑块使用强调文字 token', () => {
  const accentBlocks = [
    indexCss.match(/\.range-spring::-webkit-slider-runnable-track\s*\{([\s\S]*?)\}/)?.[1],
    indexCss.match(/\.range-spring::-webkit-slider-thumb\s*\{([\s\S]*?)\}/)?.[1],
    indexCss.match(/\.range-spring::-moz-range-thumb\s*\{([\s\S]*?)\}/)?.[1],
  ]

  for (const accentBlock of accentBlocks) {
    assert.ok(accentBlock, '缺少范围条填充段或滑块样式')
    assert.match(accentBlock, /var\(--app-accent-label\)/)
    assert.doesNotMatch(accentBlock, /var\(--app-accent-fill\)/)
  }
})

test('Konsta iOS 范围条隐藏蓝色按压光效', () => {
  const thumbShadowOverride = indexCss.match(
    /\.k-ios\s+\.k-range\s*>\s*\.pointer-events-none\s*>\s*\[class~="shadow-primary\/75"\]\s*\{([\s\S]*?)\}/,
  )?.[1]

  assert.ok(thumbShadowOverride, '缺少 Konsta iOS 范围条按压光效覆盖')
  assert.match(thumbShadowOverride, /display:\s*none/)
})

test('Konsta 范围条隐藏原生输入外观但保留交互层', () => {
  const nativeInputOverride = indexCss.match(
    /\.k-range\s*>\s*input\[type="range"\]\s*\{([\s\S]*?)\}/,
  )?.[1]

  assert.ok(nativeInputOverride, '缺少 Konsta 范围条原生输入外观覆盖')
  assert.match(nativeInputOverride, /opacity:\s*0/)
  assert.doesNotMatch(nativeInputOverride, /display:\s*none|visibility:\s*hidden|pointer-events:\s*none/)
})

test('Konsta 暗色表面 tint 使用三级表面 token', () => {
  assert.match(indexCss, /--color-ios-dark-surface-1-tint:\s*var\(--app-surface-tertiary\);/)
})

test('Konsta 填充主按钮按交互状态使用主操作语义 token', () => {
  const base = indexCss.match(/\.k-button\.app-primary-fill\.bg-primary\s*\{([\s\S]*?)\}/)?.[1]
  const hover = indexCss.match(/\.k-button\.app-primary-fill\.bg-primary:not\(:disabled\):hover\s*\{([\s\S]*?)\}/)?.[1]
  const pressed = indexCss.match(/\.k-button\.app-primary-fill\.bg-primary:not\(:disabled\):active\s*\{([\s\S]*?)\}/)?.[1]
  const disabled = indexCss.match(/\.k-button\.app-primary-fill:disabled\s*\{([\s\S]*?)\}/)?.[1]

  assert.match(base ?? '', /background-color:\s*var\(--app-accent-fill\)/)
  assert.match(base ?? '', /color:\s*var\(--app-on-accent\)/)
  assert.match(hover ?? '', /background-color:\s*var\(--app-accent-fill-hover\)/)
  assert.match(pressed ?? '', /background-color:\s*var\(--app-accent-fill-pressed\)/)
  assert.match(disabled ?? '', /background-color:\s*var\(--app-disabled-fill\)/)
  assert.match(disabled ?? '', /color:\s*var\(--app-disabled-label\)/)

  const hoverMedia = [...indexCss.matchAll(/@media \(hover: hover\) and \(pointer: fine\)\s*\{([\s\S]*?)\n\}/g)]
    .map(match => match[1])
    .find(block => block.includes('.k-button.app-primary-fill.bg-primary:not(:disabled):hover'))
  assert.ok(hoverMedia, '填充主按钮 hover 必须仅在支持 hover 的精细指针设备启用')
})
