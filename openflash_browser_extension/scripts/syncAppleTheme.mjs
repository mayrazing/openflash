import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const THEME_NAMES = ['light', 'dark', 'lightContrast', 'darkContrast']

const scriptPath = fileURLToPath(import.meta.url)
const extensionRoot = resolve(dirname(scriptPath), '..')
const frontendThemePath = resolve(extensionRoot, '../openflash_user/openflash_front/src/index.css')
const generatedThemePath = resolve(extensionRoot, 'src/ui/appleTheme.generated.css')
const generatedColorsPath = resolve(extensionRoot, 'src/ui/appleColors.generated.js')

export function readThemeBlocks(cssText) {
  return Object.fromEntries(THEME_NAMES.map((name) => {
    const start = `/* app-theme:${name}:start */`
    const end = `/* app-theme:${name}:end */`
    const startIndex = cssText.indexOf(start)
    const endIndex = cssText.indexOf(end)
    const duplicateStart = cssText.indexOf(start, startIndex + start.length)
    const duplicateEnd = cssText.indexOf(end, endIndex + end.length)
    if (
      startIndex < 0
      || endIndex < startIndex
      || duplicateStart >= 0
      || duplicateEnd >= 0
    ) {
      throw new Error(`无法唯一读取主题块: ${name}`)
    }
    return [name, cssText.slice(startIndex, endIndex + end.length)]
  }))
}

export function renderExtensionTheme(blocks) {
  return `/* Generated from openflash_user/openflash_front/src/index.css.\n * Run \`npm run theme:sync\` to update.\n */\n\n:root, .openflash-konsta-root {\n${blocks.light}\n}\n\n.dark {\n${blocks.dark}\n}\n\n@media (prefers-contrast: more) {\n  :root, .openflash-konsta-root {\n${blocks.lightContrast}\n  }\n\n  .dark {\n${blocks.darkContrast}\n  }\n}\n`
}

function readLightColor(lightBlock, token) {
  const match = lightBlock.match(new RegExp(`${token}:\\s*([^;]+);`))
  if (!match) {
    throw new Error(`无法读取主题颜色: ${token}`)
  }
  return match[1]
}

function renderBadgeColors(lightBlock) {
  const success = readLightColor(lightBlock, '--app-success-fill')
  const warning = readLightColor(lightBlock, '--app-warning-fill')
  const error = readLightColor(lightBlock, '--app-danger-fill')
  return `// Generated from openflash_user/openflash_front/src/index.css.\n// Run \`npm run theme:sync\` to update.\nexport const badgeColors = {\n  success: '${success}',\n  warning: '${warning}',\n  error: '${error}',\n}\n`
}

async function readGenerated(path) {
  try {
    return await readFile(path, 'utf8')
  } catch (error) {
    if (error.code === 'ENOENT') return null
    throw error
  }
}

export async function syncAppleTheme({ mode }) {
  const frontend = await readFile(frontendThemePath, 'utf8')
  const blocks = readThemeBlocks(frontend)
  const generatedTheme = renderExtensionTheme(blocks)
  const generatedColors = renderBadgeColors(blocks.light)

  if (mode === 'write') {
    await mkdir(dirname(generatedThemePath), { recursive: true })
    await writeFile(generatedThemePath, generatedTheme)
    await writeFile(generatedColorsPath, generatedColors)
    return
  }

  if (mode === 'check') {
    const [currentTheme, currentColors] = await Promise.all([
      readGenerated(generatedThemePath),
      readGenerated(generatedColorsPath),
    ])
    if (currentTheme !== generatedTheme || currentColors !== generatedColors) {
      throw new Error('Apple 主题生成文件已过期, 请运行 npm run theme:sync')
    }
    return
  }

  throw new Error(`未知同步模式: ${mode}`)
}

if (process.argv[1] && resolve(process.argv[1]) === scriptPath) {
  const argument = process.argv[2]
  const mode = argument === '--write' ? 'write' : argument === '--check' ? 'check' : null
  await syncAppleTheme({ mode })
}
