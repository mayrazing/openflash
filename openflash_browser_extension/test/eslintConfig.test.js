import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { ESLint } from 'eslint'

const extensionRoot = fileURLToPath(new URL('..', import.meta.url))

async function jsxFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = `${directory}/${entry.name}`
    if (entry.isDirectory()) return jsxFiles(path)
    return entry.isFile() && entry.name.endsWith('.jsx') ? [path] : []
  }))
  return nested.flat()
}

test('ESLint uses react/jsx-uses-vars without JSX file-level no-unused-vars disables', async () => {
  const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))
  const configSource = await readFile(new URL('../eslint.config.js', import.meta.url), 'utf8')

  assert.match(packageJson.devDependencies['eslint-plugin-react'] || '', /^\^7\./)
  assert.match(configSource, /import react from ['"]eslint-plugin-react['"]/)
  assert.match(configSource, /react\/jsx-uses-vars['"]?\s*:\s*['"]error['"]/)

  for (const file of await jsxFiles(`${extensionRoot}/src`)) {
    const source = await readFile(file, 'utf8')
    assert.doesNotMatch(source, /eslint-disable no-unused-vars/, file)
  }
})

test('ESLint counts JSX component references as usage and still reports real unused variables', async () => {
  const eslint = new ESLint({ cwd: extensionRoot })
  const [jsxUsage] = await eslint.lintText(
    'const VisibleInJsx = () => <span />\nexport default function Probe() { return <VisibleInJsx /> }\n',
    { filePath: 'src/__jsx_usage_probe__.jsx' },
  )
  assert.equal(jsxUsage.messages.find((message) => message.ruleId === 'no-unused-vars'), undefined)

  const [realUnused] = await eslint.lintText(
    'const trulyUnused = 1\nexport default function Probe() { return <span /> }\n',
    { filePath: 'src/__real_unused_probe__.jsx' },
  )
  assert.match(
    realUnused.messages.find((message) => message.ruleId === 'no-unused-vars')?.message || '',
    /trulyUnused/,
  )
})
