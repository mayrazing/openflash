import assert from 'node:assert/strict'
import test from 'node:test'
import { ROOT_MENU_ID } from '../src/config.js'
import {
  createCommandImportHandler,
  IMPORT_DEFAULT_COMMAND,
} from '../src/commandImport.js'

test('command import ignores non-matching commands', async () => {
  const calls = []
  const handleCommandImport = createCommandImportHandler({
    async handleMenuClick(info, tab) {
      calls.push({ info, tab })
    },
  })

  const handled = await handleCommandImport('unknown-command', { id: 7 })

  assert.equal(handled, false)
  assert.deepEqual(calls, [])
})

test('command import triggers root menu import with current tab', async () => {
  const calls = []
  const tab = { id: 7 }
  const handleCommandImport = createCommandImportHandler({
    async handleMenuClick(info, targetTab) {
      calls.push({ info, tab: targetTab })
    },
  })

  await handleCommandImport(IMPORT_DEFAULT_COMMAND, tab)

  assert.deepEqual(calls, [
    {
      info: { menuItemId: ROOT_MENU_ID },
      tab,
    },
  ])
})

test('command import exposes failures to caller', async () => {
  const handleCommandImport = createCommandImportHandler({
    async handleMenuClick() {
      throw new Error('导入失败')
    },
  })

  await assert.rejects(handleCommandImport(IMPORT_DEFAULT_COMMAND, { id: 7 }), /导入失败/)
})
