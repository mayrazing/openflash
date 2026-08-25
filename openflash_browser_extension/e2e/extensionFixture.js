import { rm } from 'node:fs/promises'

export function assertSupportedReloadOutcome(outcome, browserVersion) {
  if (outcome?.kind === 'service-worker') return outcome
  if (outcome?.kind === 'extension-unloaded') {
    if (browserVersion.startsWith('149.')) return outcome
    throw new Error(`extension-unloaded is only accepted on verified Chromium 149, got ${browserVersion}`)
  }
  throw new Error(`unexpected reload outcome: ${outcome?.kind || 'missing'}`)
}

export async function cleanupExtensionProfile(context, userDataDir, remove = rm) {
  try {
    await context?.close()
  } finally {
    await remove(userDataDir, { force: true, recursive: true })
  }
}
