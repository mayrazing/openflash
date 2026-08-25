import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

/** 读取插件 manifest，确认默认导入快捷键保持为产品约定值。 */
async function readManifest() {
  return JSON.parse(await readFile(new URL('../manifest.json', import.meta.url), 'utf8'))
}

test('default import command uses Alt+Shift+D', async () => {
  const manifest = await readManifest()
  const command = manifest.commands['openflash-import-default']

  assert.equal(command.suggested_key.default, 'Alt+Shift+D')
  assert.equal(command.suggested_key.mac, 'Alt+Shift+D')
})

test('content script loads its bundled entry point without hosting the manual editor', async () => {
  const manifest = await readManifest()
  const commandSource = await readFile(new URL('../src/manualCardCommand.js', import.meta.url), 'utf8')

  assert.deepEqual(manifest.content_scripts[0].js, ['assets/contentScript.js'])
  assert.doesNotMatch(commandSource, /CONTENT_SCRIPT_FILES|tabs\.sendMessage/)
  assert.match(commandSource, /deps\.openEditor/)
})

test('manifest exposes manual card command with default shortcut', async () => {
  const manifest = await readManifest()
  const command = manifest.commands['openflash-manual-card']

  assert.ok(command)
  assert.equal(command.suggested_key.default, 'Alt+Shift+A')
  assert.equal(command.suggested_key.mac, 'Alt+Shift+A')
  assert.equal(command.description, 'Create OpenFlash card manually')
})

test('lint checks all extension source files', async () => {
  const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))

  assert.equal(packageJson.scripts.lint, 'eslint .')
})

test('lint includes shortcut setup modules through the project scope', async () => {
  const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))

  assert.equal(packageJson.scripts.lint, 'eslint .')
})
