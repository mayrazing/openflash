import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi.js'

function ok(data) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data }),
  }
}

async function installTtsCardApi(page) {
  const requests = { piperPreview: 0 }
  await installMockApi(page)
  await page.unroute('**/api/plugins/active')
  await page.unroute('**/api/plugins/installed**')
  await page.route('**/api/plugins/active', route => route.fulfill(ok(['tts', 'ai-card'])))
  await page.route('**/api/plugins/installed**', route => route.fulfill(ok(['tts', 'ai-card'])))
  await page.route('**/api/plugins/tts/engines', route => route.fulfill(ok(['piper'])))
  await page.route('**/api/tts/piper', route => {
    requests.piperPreview += 1
    return route.fulfill({ status: 200, contentType: 'audio/wav', body: 'test-wav' })
  })
  return requests
}

test('pen tap on TTS dialog text does not activate owning card AI action', async ({ page }) => {
  const requests = await installTtsCardApi(page)
  await page.goto('/deck/1')

  const speaker = page.locator('button[aria-label^="朗读"], button[aria-label^="Read aloud"]').first()
  await expect(speaker).toBeVisible()
  await page.evaluate(() => {
    window.__aiCardOpenCount = 0
    window.addEventListener('ai-card:open', () => { window.__aiCardOpenCount += 1 })
  })

  await speaker.dispatchEvent('pointerdown', { pointerType: 'pen', pointerId: 41 })
  await page.waitForTimeout(550)
  await speaker.dispatchEvent('pointerup', { pointerType: 'pen', pointerId: 41 })

  const dialog = page.getByRole('dialog', { name: /发音|pronunciation/i })
  await expect(dialog).toBeVisible()
  const description = dialog.locator('p').first()
  await description.dispatchEvent('pointerdown', { pointerType: 'pen', pointerId: 42 })
  await description.dispatchEvent('pointerup', { pointerType: 'pen', pointerId: 42 })

  await expect.poll(() => page.evaluate(() => window.__aiCardOpenCount)).toBe(0)

  const piper = dialog.locator('li a').first()
  await piper.dispatchEvent('pointerdown', { pointerType: 'pen', pointerId: 43 })
  await piper.dispatchEvent('pointerup', { pointerType: 'pen', pointerId: 43 })

  await expect.poll(() => requests.piperPreview).toBe(1)
  await expect.poll(() => page.evaluate(() => window.__aiCardOpenCount)).toBe(0)
})
