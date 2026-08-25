import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import test from 'node:test'
import { readThemeBlocks, renderExtensionTheme } from '../scripts/syncAppleTheme.mjs'
import { watchSystemTheme } from '../src/ui/systemTheme.js'

const sourceRoot = new URL('../src/', import.meta.url)

const completeThemeFixture = `
/* app-theme:light:start */
--fixture: light;
/* app-theme:light:end */
/* app-theme:dark:start */
--fixture: dark;
/* app-theme:dark:end */
/* app-theme:lightContrast:start */
--fixture: light-contrast;
/* app-theme:lightContrast:end */
/* app-theme:darkContrast:start */
--fixture: dark-contrast;
/* app-theme:darkContrast:end */
`

async function readJsxFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const url = new URL(entry.name + (entry.isDirectory() ? '/' : ''), directory)
    if (entry.isDirectory()) return readJsxFiles(url)
    return entry.name.endsWith('.jsx') ? [await readFile(url, 'utf8')] : []
  }))
  return nested.flat()
}

function extractUniqueBraceBlock(cssText, header) {
  const opening = `${header} {`
  const startIndex = cssText.indexOf(opening)
  assert.notEqual(startIndex, -1, `缺少区块: ${header}`)
  assert.equal(cssText.indexOf(opening, startIndex + opening.length), -1, `区块重复: ${header}`)

  let depth = 0
  for (let index = startIndex + opening.length - 1; index < cssText.length; index += 1) {
    if (cssText[index] === '{') depth += 1
    if (cssText[index] !== '}') continue
    depth -= 1
    if (depth === 0) return cssText.slice(startIndex, index + 1)
  }

  assert.fail(`区块未闭合: ${header}`)
}

test('extension Apple theme is generated from frontend theme blocks', async () => {
  const frontend = await readFile(new URL('../../openflash_user/openflash_front/src/index.css', import.meta.url), 'utf8')
  const generated = await readFile(new URL('../src/ui/appleTheme.generated.css', import.meta.url), 'utf8')
  assert.equal(generated, renderExtensionTheme(readThemeBlocks(frontend)))
})

test('generated theme routes each appearance through its fixed selector and media query', async () => {
  const generated = await readFile(new URL('../src/ui/appleTheme.generated.css', import.meta.url), 'utf8')
  const highContrast = extractUniqueBraceBlock(generated, '@media (prefers-contrast: more)')

  assert.match(generated, /:root, \.openflash-konsta-root \{\n\/\* app-theme:light:start \*\/[\s\S]*?\/\* app-theme:light:end \*\/\n\}/)
  assert.match(generated, /\.dark \{\n\/\* app-theme:dark:start \*\/[\s\S]*?\/\* app-theme:dark:end \*\/\n\}/)
  assert.match(highContrast, /^@media \(prefers-contrast: more\) \{\n {2}:root, \.openflash-konsta-root \{\n\/\* app-theme:lightContrast:start \*\/[\s\S]*?\/\* app-theme:lightContrast:end \*\/\n {2}\}/)
  assert.match(highContrast, /\n {2}\.dark \{\n\/\* app-theme:darkContrast:start \*\/[\s\S]*?\/\* app-theme:darkContrast:end \*\/\n {2}\}\n\}$/)
})

test('theme reader rejects every missing start or end marker', () => {
  for (const name of ['light', 'dark', 'lightContrast', 'darkContrast']) {
    for (const boundary of ['start', 'end']) {
      const marker = `/* app-theme:${name}:${boundary} */`
      assert.throws(
        () => readThemeBlocks(completeThemeFixture.replace(marker, '')),
        new RegExp(`无法唯一读取主题块: ${name}`),
      )
    }
  }
})

test('theme reader rejects every duplicated start or end marker', () => {
  for (const name of ['light', 'dark', 'lightContrast', 'darkContrast']) {
    for (const boundary of ['start', 'end']) {
      const marker = `/* app-theme:${name}:${boundary} */`
      assert.throws(
        () => readThemeBlocks(`${completeThemeFixture}\n${marker}`),
        new RegExp(`无法唯一读取主题块: ${name}`),
      )
    }
  }
})

test('generated theme includes all four appearances and semantic colors', async () => {
  const generated = await readFile(new URL('../src/ui/appleTheme.generated.css', import.meta.url), 'utf8')
  assert.match(generated, /:root, \.openflash-konsta-root/)
  for (const marker of ['light', 'dark', 'lightContrast', 'darkContrast']) {
    assert.match(generated, new RegExp(`app-theme:${marker}:start`))
  }
  for (const token of [
    '--app-background', '--app-surface-primary', '--app-label-primary',
    '--app-separator', '--app-accent-fill', '--app-danger-fill',
    '--app-success-fill', '--app-disabled-label',
  ]) {
    assert.match(generated, new RegExp(`${token}:`))
  }
})

test('badge colors are generated from frontend light semantic colors', async () => {
  const frontend = await readFile(new URL('../../openflash_user/openflash_front/src/index.css', import.meta.url), 'utf8')
  const generated = await import('../src/ui/appleColors.generated.js')
  const light = readThemeBlocks(frontend).light
  assert.equal(generated.badgeColors.success, light.match(/--app-success-fill:\s*([^;]+);/)[1])
  assert.equal(generated.badgeColors.warning, light.match(/--app-warning-fill:\s*([^;]+);/)[1])
  assert.equal(generated.badgeColors.error, light.match(/--app-danger-fill:\s*([^;]+);/)[1])
})

test('page and Shadow DOM styles map Konsta colors to Apple semantic colors', async () => {
  for (const filename of ['app.css', 'shadow.css']) {
    const css = await readFile(new URL(`../src/ui/${filename}`, import.meta.url), 'utf8')
    assert.match(css, /@import "tailwindcss";/)
    assert.match(css, /@import "konsta\/react\/theme\.css";/)
    assert.match(css, /@import "\.\/appleTheme\.generated\.css";/)
    assert.doesNotMatch(
      css,
      /--color-brand-[\w-]+:\s*var\(/,
      `${filename} 不能用 CSS var 覆盖 Konsta 要求的十六进制 brand color`,
    )
    for (const mapping of [
      '--color-app-background: var(--app-background);',
      '--color-app-label-primary: var(--app-label-primary);',
      '--k-color-primary: var(--app-accent-label);',
      '--k-color-ios-primary: var(--app-accent-label);',
      '--k-color-ios-primary-tint: var(--app-accent-fill-hover);',
      '--k-color-ios-primary-shade: var(--app-accent-fill-pressed);',
      '--k-hairline-color: var(--app-separator);',
      '--color-ios-light-surface: var(--app-background);',
      '--color-ios-light-surface-1: var(--app-surface-primary);',
      '--color-ios-light-surface-1-shade: var(--app-surface-secondary);',
      '--color-ios-light-surface-1-tint: var(--app-surface-tertiary);',
      '--color-ios-light-surface-2: var(--app-surface-secondary);',
      '--color-ios-light-surface-3: var(--app-surface-primary);',
      '--color-ios-light-surface-variant: var(--app-surface-secondary);',
      '--color-ios-dark-surface: var(--app-background);',
      '--color-ios-dark-surface-1: var(--app-surface-primary);',
      '--color-ios-dark-surface-1-shade: var(--app-surface-secondary);',
      '--color-ios-dark-surface-1-tint: var(--app-surface-tertiary);',
      '--color-ios-dark-surface-2: var(--app-surface-secondary);',
      '--color-ios-dark-surface-3: var(--app-surface-primary);',
      '--color-ios-dark-surface-variant: var(--app-surface-secondary);',
    ]) {
      assert.ok(css.includes(mapping), `${filename} 缺少语义映射: ${mapping}`)
    }
  }
})

test('extension UI source does not use forbidden fixed appearance utilities', async () => {
  const appCss = await readFile(new URL('../src/ui/app.css', import.meta.url), 'utf8')
  const shadowCss = await readFile(new URL('../src/ui/shadow.css', import.meta.url), 'utf8')
  const source = [appCss, shadowCss, ...await readJsxFiles(sourceRoot)].join('\n')
  for (const forbidden of ['bg-ios-', 'text-black', 'dark:text-white']) {
    assert.ok(!source.includes(forbidden), `禁止固定外观配色: ${forbidden}`)
  }
  assert.doesNotMatch(shadowCss, /#[0-9a-f]{3,8}\b/i)
})

function createThemeHarness(matches) {
  const classes = new Set()
  const listeners = new Set()
  const query = {
    matches,
    addEventListener: (type, listener) => type === 'change' && listeners.add(listener),
    removeEventListener: (type, listener) => type === 'change' && listeners.delete(listener),
  }
  const target = {
    classList: {
      contains: (name) => classes.has(name),
      toggle: (name, force) => force ? classes.add(name) : classes.delete(name),
    },
  }
  return { classes, listeners, query, target }
}

test('system theme applies initial dark appearance', () => {
  const harness = createThemeHarness(true)

  watchSystemTheme(harness.target, () => harness.query)

  assert.equal(harness.target.classList.contains('dark'), true)
})

test('system theme follows appearance changes', () => {
  const harness = createThemeHarness(true)
  watchSystemTheme(harness.target, () => harness.query)

  harness.query.matches = false
  for (const listener of harness.listeners) listener()

  assert.equal(harness.target.classList.contains('dark'), false)
})

test('system theme cleanup removes appearance listener', () => {
  const harness = createThemeHarness(true)
  const cleanup = watchSystemTheme(harness.target, () => harness.query)

  cleanup()

  assert.equal(harness.listeners.size, 0)
})

test('build checks generated theme before deleting dist', async () => {
  const buildScript = await readFile(new URL('../scripts/build.mjs', import.meta.url), 'utf8')
  const checkIndex = buildScript.indexOf("await syncAppleTheme({ mode: 'check' })")
  const removeIndex = buildScript.indexOf('await rm(outDir')

  assert.ok(checkIndex >= 0, 'build 缺少 Apple 主题一致性检查')
  assert.ok(checkIndex < removeIndex, 'Apple 主题检查必须在删除 dist 前执行')
})
