import assert from 'node:assert/strict'
import test from 'node:test'
import * as extensionFixture from '../e2e/extensionFixture.js'

const { cleanupExtensionProfile } = extensionFixture

test('reload outcome only accepts a new worker or Chromium 149 extension unload', () => {
  assert.equal(typeof extensionFixture.assertSupportedReloadOutcome, 'function')
  assert.equal(
    extensionFixture.assertSupportedReloadOutcome({ kind: 'service-worker' }, '150.0.0.0').kind,
    'service-worker',
  )
  assert.equal(
    extensionFixture.assertSupportedReloadOutcome({ kind: 'extension-unloaded' }, '149.0.7827.55').kind,
    'extension-unloaded',
  )
  assert.throws(
    () => extensionFixture.assertSupportedReloadOutcome({ kind: 'context-closed' }, '149.0.7827.55'),
    /unexpected reload outcome: context-closed/,
  )
  assert.throws(
    () => extensionFixture.assertSupportedReloadOutcome({ kind: 'extension-unloaded' }, '150.0.0.0'),
    /only accepted on verified Chromium 149/,
  )
})

test('extension profile cleanup removes the profile when context creation failed', async () => {
  const removedProfiles = []

  await cleanupExtensionProfile(null, '/tmp/openflash-profile-launch-failed', async (profile) => {
    removedProfiles.push(profile)
  })

  assert.deepEqual(removedProfiles, ['/tmp/openflash-profile-launch-failed'])
})

test('extension profile cleanup removes the profile when context close fails', async () => {
  const removedProfiles = []
  const context = {
    async close() {
      throw new Error('context close failed')
    },
  }

  await assert.rejects(
    cleanupExtensionProfile(context, '/tmp/openflash-profile-close-failed', async (profile) => {
      removedProfiles.push(profile)
    }),
    /context close failed/,
  )

  assert.deepEqual(removedProfiles, ['/tmp/openflash-profile-close-failed'])
})
