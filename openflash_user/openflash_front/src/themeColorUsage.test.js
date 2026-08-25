import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const sourceRoot = fileURLToPath(new URL('.', import.meta.url))
const indexCss = readFileSync(join(sourceRoot, 'index.css'), 'utf8')
const scanRoots = ['App.jsx', 'pages', 'components', 'plugins', 'lib']
const fixedUtility = /(?:text|bg|border|ring|fill|stroke|decoration)-(?:gray|slate|zinc|neutral|stone|red|orange|amber|yellow|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|pink|rose)-\d{2,3}(?:\/\d+)?/g
const namedUtility = /(?:text|bg|border|ring|fill|stroke|decoration)-(?:black|white)(?:\/\d+)?/g
const rawColor = /#[\da-f]{3,8}\b|\b(?:rgb|rgba|hsl|hsla)\([^)]*\)/gi
const legacyPrimaryUtility = /\bk-color-brand-primary\b/g
const brandUtility = /(?<![\w-])(?:[a-z-]+:)*(?:text|bg|border|ring|fill|stroke|decoration)-brand-(?:primary|danger|warning)(?:\/\d+)?(?![\w-])/g
const opacityUtility = /(?<![\w-])(?:[a-z-]+:)*opacity-(?:\[[^\]\s"'`]+\]|\d+(?:\.\d+)?)(?![\w-])/g
const allowedOpacityContexts = [
  {
    path: 'lib/sideScrollHints.js',
    context: "'scale-100 text-app-label-tertiary opacity-40 dark:opacity-15'",
    utilities: ['opacity-40', 'dark:opacity-15'],
  },
  {
    path: 'components/CardMoveModal.jsx',
    context: '<p className="mb-3 text-sm opacity-65">',
    utilities: ['opacity-65'],
  },
]

function walk(path) {
  if (!statSync(path).isDirectory()) return [path]
  return readdirSync(path).flatMap(name => walk(join(path, name)))
}

function collectRuntimeFiles() {
  return scanRoots
    .flatMap(path => walk(join(sourceRoot, path)))
    .filter(path => ['.js', '.jsx'].includes(extname(path)) && !path.endsWith('.test.js'))
    .map(path => ({
      path: relative(sourceRoot, path).replaceAll('\\', '/'),
      source: readFileSync(path, 'utf8'),
    }))
}

function collectButtonOpeningTags(source) {
  const tags = []
  for (const match of source.matchAll(/<Button\b/g)) {
    let index = match.index + match[0].length
    let braceDepth = 0
    let quote = null
    let escaped = false
    for (; index < source.length; index += 1) {
      const character = source[index]
      if (quote) {
        if (escaped) escaped = false
        else if (character === '\\') escaped = true
        else if (character === quote) quote = null
        continue
      }
      if (character === '"' || character === "'" || character === '`') quote = character
      else if (character === '{') braceDepth += 1
      else if (character === '}') braceDepth -= 1
      else if (character === '>' && braceDepth === 0) break
    }
    tags.push({
      source: source.slice(match.index, index + 1),
      line: source.slice(0, match.index).split('\n').length,
    })
  }
  return tags
}

function inverseExpression(expression) {
  for (const [operator, inverse] of [['!==', '==='], ['===', '!=='], ['!=', '=='], ['==', '!=']]) {
    if (expression.includes(operator)) return expression.replace(operator, inverse)
  }
  return expression.startsWith('!') ? expression.slice(1) : `!${expression}`
}

function hasGuaranteedNonFilledVariant(tag) {
  if (/\s(?:clear|tonal|outline)(?:\s*=\s*\{\s*true\s*\})?(?=\s|\/?>)/.test(tag)) return true
  const expressions = [...tag.matchAll(/\s(?:clear|tonal|outline)\s*=\s*\{([^{}]+)\}/g)]
    .map(match => match[1].replace(/\s+/g, ''))
  return expressions.some(expression => expressions.includes(inverseExpression(expression)))
}

function unmarkedFilledButtons(files) {
  return files.flatMap(({ path, source }) => collectButtonOpeningTags(source).flatMap(({ source: tag, line }) => {
    const statusSemantic = /bg-app-(?:danger|warning|success)-fill/.test(tag)
    if (hasGuaranteedNonFilledVariant(tag) || statusSemantic || /\bapp-primary-fill\b/.test(tag)) return []
    return [`${path}:${line}: 填充主按钮缺少 app-primary-fill`]
  }))
}

function statusFilledButtonFailures(files) {
  return files.flatMap(({ path, source }) => collectButtonOpeningTags(source).flatMap(({ source: tag, line }) => {
    if (hasGuaranteedNonFilledVariant(tag)) return []
    const tone = tag.match(/(?:k-color-brand-|bg-app-)(danger|warning|success)(?:-fill)?/)?.[1]
    if (!tone) return []
    const required = [
      `bg-app-${tone}-fill`,
      `hover:bg-app-${tone}-hover`,
      `active:bg-app-${tone}-pressed`,
      `text-app-on-${tone}`,
      'disabled:bg-app-disabled-fill',
      'disabled:text-app-disabled-label',
    ]
    const missing = required.filter(className => !tag.includes(className))
    if (/k-color-brand-(?:danger|warning|success)/.test(tag)) missing.push('filled 状态按钮禁用 k-color-brand-*')
    return missing.map(className => `${path}:${line}: ${tone} 填充按钮缺少 ${className}`)
  }))
}

function isAllowedOpacity(path, source, match) {
  const utility = match[0]
  const baseUtility = utility.slice(utility.lastIndexOf(':') + 1)
  if (baseUtility === 'opacity-0' || baseUtility === 'opacity-100') return true

  return allowedOpacityContexts.some(allowed => {
    if (allowed.path !== path || !allowed.utilities.includes(utility)) return false
    const contextStart = source.lastIndexOf(allowed.context, match.index)
    return contextStart !== -1 && match.index < contextStart + allowed.context.length
  })
}

function opacityFailures(files) {
  return files.flatMap(({ path, source }) => {
    return [...source.matchAll(opacityUtility)].filter(match => !isAllowedOpacity(path, source, match)).map(match => {
      const line = source.slice(0, match.index).split('\n').length
      return `${path}:${line}: ${match[0]}`
    })
  })
}

function colorFailures(files) {
  return files.flatMap(({ path, source }) => {
    const withoutFireworks = path === 'pages/Summary.jsx'
      ? source.replace(/const FIREWORK_COLORS = \[[^\]]*\]/, '')
      : source
    return [...withoutFireworks.matchAll(fixedUtility), ...withoutFireworks.matchAll(namedUtility), ...withoutFireworks.matchAll(rawColor), ...withoutFireworks.matchAll(legacyPrimaryUtility), ...withoutFireworks.matchAll(brandUtility)]
      .map(match => `${path}: ${match[0]}`)
  })
}

test('运行时界面只消费语义颜色', () => {
  assert.deepEqual(colorFailures(collectRuntimeFiles()), [])
})

test('运行时颜色扫描拒绝全部 brand Tailwind utility', () => {
  const source = 'text-brand-primary bg-brand-danger border-brand-warning hover:ring-brand-primary fill-brand-danger stroke-brand-warning decoration-brand-primary'
  assert.deepEqual(colorFailures([{ path: 'fixture.jsx', source }]), [
    'fixture.jsx: text-brand-primary',
    'fixture.jsx: bg-brand-danger',
    'fixture.jsx: border-brand-warning',
    'fixture.jsx: hover:ring-brand-primary',
    'fixture.jsx: fill-brand-danger',
    'fixture.jsx: stroke-brand-warning',
    'fixture.jsx: decoration-brand-primary',
  ])
})

test('项目不注册固定 brand Tailwind 颜色, 仅保留运行时语义绑定', () => {
  const rootBlock = indexCss.match(/:root\s*\{([\s\S]*?)\n\}/)?.[1] ?? ''

  assert.doesNotMatch(indexCss, /@theme\s*\{[^}]*--color-brand-(?:primary|danger|warning):\s*#[\da-f]+;/is)
  assert.match(rootBlock, /--color-brand-primary:\s*var\(--app-accent-label\);/)
  assert.match(rootBlock, /--color-brand-danger:\s*var\(--app-danger-label\);/)
  assert.match(rootBlock, /--color-brand-warning:\s*var\(--app-warning-label\);/)
})

test('可读内容和控件不使用会降低组合对比度的低透明度', () => {
  assert.deepEqual(opacityFailures(collectRuntimeFiles()), [])
})

test('透明度扫描覆盖任意数值和交互变体, 仅放行完全透明或完全不透明', () => {
  const failuresFor = source => opacityFailures([{ path: 'fixture.jsx', source }])

  assert.deepEqual(
    failuresFor('opacity-61 opacity-99 opacity-[0.45] hover:opacity-99 disabled:opacity-[0.45]'),
    [
      'fixture.jsx:1: opacity-61',
      'fixture.jsx:1: opacity-99',
      'fixture.jsx:1: opacity-[0.45]',
      'fixture.jsx:1: hover:opacity-99',
      'fixture.jsx:1: disabled:opacity-[0.45]',
    ],
  )
  assert.deepEqual(failuresFor('opacity-0 opacity-100 hover:opacity-0 disabled:opacity-100'), [])
})

test('装饰透明度豁免绑定精确文件和 class 上下文', () => {
  const failuresFor = (path, source) => opacityFailures([{ path, source }])
  const sideHintClass = "'scale-100 text-app-label-tertiary opacity-40 dark:opacity-15'"
  const sideHintSource = `function sideHintClass(side) {\n    return ${sideHintClass}\n  }`
  const moveDescription = '<p className="mb-3 text-sm opacity-65">'

  assert.deepEqual(failuresFor('lib/sideScrollHints.js', sideHintSource), [])
  assert.deepEqual(
    failuresFor('lib/sideScrollHints.js', sideHintSource.replace(sideHintClass, `${sideHintClass} opacity-55`)),
    ['lib/sideScrollHints.js:2: opacity-55'],
  )
  assert.deepEqual(
    failuresFor('fixture.jsx', sideHintClass),
    ['fixture.jsx:1: opacity-40', 'fixture.jsx:1: dark:opacity-15'],
  )

  assert.deepEqual(failuresFor('components/CardMoveModal.jsx', moveDescription), [])
  assert.deepEqual(
    failuresFor('components/CardMoveModal.jsx', '<div className="opacity-65">'),
    ['components/CardMoveModal.jsx:1: opacity-65'],
  )
  assert.deepEqual(
    failuresFor('fixture.jsx', moveDescription),
    ['fixture.jsx:1: opacity-65'],
  )
})

test('Konsta 填充主按钮声明主操作填充语义', () => {
  assert.deepEqual(unmarkedFilledButtons(collectRuntimeFiles()), [])
})

test('Konsta 状态填充按钮声明完整交互和禁用语义', () => {
  assert.deepEqual(statusFilledButtonFailures(collectRuntimeFiles()), [])
})

test('ConfirmDialog 和可点击成功 Toast 声明完整状态交互语义', () => {
  const files = Object.fromEntries(collectRuntimeFiles().map(file => [file.path, file.source]))
  const dangerClasses = 'bg-app-danger-fill hover:bg-app-danger-hover active:bg-app-danger-pressed text-app-on-danger disabled:bg-app-disabled-fill disabled:text-app-disabled-label'
  const warningClasses = 'bg-app-warning-fill hover:bg-app-warning-hover active:bg-app-warning-pressed text-app-on-warning disabled:bg-app-disabled-fill disabled:text-app-disabled-label'
  const successToastClasses = '!bg-app-success-fill hover:!bg-app-success-hover active:!bg-app-success-pressed'
  const dangerToastClasses = "colors={{ bgIos: '!bg-app-danger-fill', textIos: '!text-app-on-danger' }}"

  assert.ok(files['components/ConfirmDialog.jsx'].includes(dangerClasses))
  assert.equal(files['pages/DeckDetail.jsx'].split(warningClasses).length - 1, 2)
  assert.ok(files['plugins/ai-card/AiNotificationToast.jsx'].includes(successToastClasses))
  assert.ok(files['plugins/ai-card/AiNotificationToast.jsx'].includes(dangerToastClasses))
  assert.ok(files['plugins/tts/TtsToast.jsx'].includes(dangerToastClasses))
})

test('Konsta 按钮分类保留动态 filled 分支检查', () => {
  const failuresFor = source => unmarkedFilledButtons([{ path: 'fixture.jsx', source }])

  assert.equal(failuresFor('<Button tonal rounded>次操作</Button>').length, 0)
  assert.equal(failuresFor('<Button tonal={count > 0} onClick={() => setOpen(true)}>安装</Button>').length, 1)
  assert.equal(failuresFor('<Button tonal={active} outline={!active}>筛选</Button>').length, 0)
  assert.equal(failuresFor('<Button className="app-primary-fill">保存</Button>').length, 0)
  assert.equal(failuresFor('<Button className="bg-app-danger-fill text-app-on-danger">删除</Button>').length, 0)
})

test('Konsta 状态填充按钮分类不被单个状态类豁免', () => {
  const failuresFor = source => statusFilledButtonFailures([{ path: 'fixture.jsx', source }])
  const completeDanger = '<Button className="bg-app-danger-fill hover:bg-app-danger-hover active:bg-app-danger-pressed text-app-on-danger disabled:bg-app-disabled-fill disabled:text-app-disabled-label">删除</Button>'

  assert.equal(failuresFor('<Button className="k-color-brand-danger">删除</Button>').length, 7)
  assert.equal(failuresFor('<Button className="bg-app-danger-fill text-app-on-danger">删除</Button>').length, 4)
  assert.equal(failuresFor(completeDanger).length, 0)
  assert.equal(failuresFor('<Button clear className="k-color-brand-danger">删除</Button>').length, 0)
})
