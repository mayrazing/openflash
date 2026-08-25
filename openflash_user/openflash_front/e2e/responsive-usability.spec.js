import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi.js'

const overflowViewports = [
  { width: 320, height: 568, name: 'small-phone' },
  { width: 667, height: 375, name: 'phone-landscape' },
  { width: 1024, height: 768, name: 'tablet-landscape' },
  { width: 1280, height: 360, name: 'desktop-short' },
]

const interactionViewports = [
  { width: 320, height: 568, name: 'small-phone' },
  { width: 667, height: 375, name: 'phone-landscape' },
  { width: 1280, height: 720, name: 'desktop' },
]

const routes = [
  '/',
  '/deck/1',
  '/deck/1/practice',
  '/settings',
  '/marketplace',
  '/mastered',
  '/deck/1/settings',
]

const routeReadyChecks = {
  '/': page => expect(page.getByText('Responsive Deck').first()).toBeVisible(),
  '/deck/1': page => expect(page.getByText('Responsive Deck').first()).toBeVisible(),
  '/deck/1/practice': page => expect(page.getByRole('button', { name: /智能复习|Smart review|Random|随机/ }).first()).toBeVisible(),
  '/settings': page => expect(page.getByRole('heading', { name: /设置|Settings/ }).first()).toBeVisible(),
  '/marketplace': page => expect(page.getByRole('heading', { name: /插件|Plugins|Lisäosat/ }).first()).toBeVisible(),
  '/mastered': page => expect(page.getByRole('heading', { name: /已掌握卡包|Mastered/ }).first()).toBeVisible(),
  '/deck/1/settings': page => expect(page.getByRole('heading', { name: /卡包设置|Deck settings/ }).first()).toBeVisible(),
}

async function gotoReady(page, route) {
  await page.goto(route)
  await expect(page.locator('#root')).toBeVisible()
  await routeReadyChecks[route]?.(page)
}

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => ({
    html: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(overflow.html).toBeLessThanOrEqual(overflow.viewport + 1)
  expect(overflow.body).toBeLessThanOrEqual(overflow.viewport + 1)
}

async function expectActionable(locator) {
  await locator.scrollIntoViewIfNeeded()
  await expect(locator).toBeVisible()
  await locator.click({ trial: true })
  const box = await locator.boundingBox()
  expect(box).not.toBeNull()
  expect(box?.width ?? 0).toBeGreaterThan(20)
  expect(box?.height ?? 0).toBeGreaterThan(20)
}

async function revealPracticeAnswer(page) {
  const answerPane = page.locator('div.flex.flex-col.items-center.w-full.flex-1.cursor-pointer').first()
  await expect(answerPane).toBeVisible()
  const box = await answerPane.boundingBox()
  expect(box).not.toBeNull()
  await answerPane.click({
    position: {
      x: (box?.width ?? 0) / 2,
      y: Math.min(Math.max(((box?.height ?? 0) * 0.2), 24), (box?.height ?? 0) - 8),
    },
  })
}

test.describe('Konsta accessibility behavior', () => {
  test('navbar back button works from the keyboard', async ({ page }) => {
    await installMockApi(page)
    await gotoReady(page, '/settings')

    const backButton = page.getByRole('link', { name: /返回|Back/ }).first()
    await backButton.focus()
    await backButton.press('Enter')

    await expect(page).toHaveURL(/\/$/)
  })

  test('dialog traps focus, closes with Escape, and restores focus', async ({ page }) => {
    await installMockApi(page)
    await gotoReady(page, '/')

    const deleteButton = page.getByRole('button', { name: /删除|Delete/ }).first()
    await deleteButton.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect.poll(() => page.evaluate(() => (
      document.querySelector('[role="dialog"]')?.contains(document.activeElement) ?? false
    ))).toBe(true)

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(deleteButton).toBeFocused()
  })

  test('auth inputs retain accessible names', async ({ page }) => {
    await page.route('**/api/auth/me', route => route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 401 }),
    }))
    await page.goto('/auth')

    await expect(page.getByLabel(/用户名|Username/)).toBeVisible()
    await expect(page.getByLabel(/密码|Password/)).toBeVisible()
  })
})

for (const viewport of overflowViewports) {
  test.describe(`responsive overflow ${viewport.name}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })
      await installMockApi(page)
    })

    for (const route of routes) {
      test(`${route} has no horizontal overflow`, async ({ page }) => {
        await gotoReady(page, route)
        await expectNoHorizontalOverflow(page)
      })
    }
  })
}

for (const viewport of interactionViewports) {
  test.describe(`responsive actions ${viewport.name}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })
      await installMockApi(page)
    })

    test('home selection action bar remains clickable', async ({ page }) => {
      await gotoReady(page, '/')
      const firstDeck = page.getByText('Responsive Deck').first()
      await expect(firstDeck).toBeVisible()
      await firstDeck.dispatchEvent('pointerdown', { pointerId: 1, clientX: 40, clientY: 120 })
      const cancelButton = page.getByRole('button', { name: /取消|Cancel/ }).last()
      await expect(cancelButton).toBeVisible()
      await firstDeck.dispatchEvent('pointerup', { pointerId: 1, clientX: 40, clientY: 120 })
      await expectActionable(cancelButton)
    })

    test('confirm dialog action buttons are reachable', async ({ page }) => {
      await gotoReady(page, '/')
      await page.getByRole('button', { name: /删除|Delete/ }).first().click()
      await expectActionable(page.getByRole('button', { name: /取消|Cancel/ }).last())
      await expectActionable(page.getByRole('button', { name: /确认|确定|Confirm|Delete|删除/ }).last())
      await expectNoHorizontalOverflow(page)
    })

    test('csv import dialog action buttons are reachable', async ({ page }) => {
      await gotoReady(page, '/deck/1')
      await page.getByRole('button', { name: /批量导入|Import/ }).first().click()
      await expectActionable(page.getByRole('button', { name: /取消|Cancel/ }).last())
      await expectActionable(page.getByRole('button', { name: /导入|Import/ }).last())
      await expectNoHorizontalOverflow(page)
    })

    test('plugin install dialog action buttons are reachable', async ({ page }) => {
      await gotoReady(page, '/marketplace')
      await expect(page.getByText('Responsive Test Plugin')).toBeVisible()
      await page.getByRole('button', { name: /^安装$|^Install$/ }).first().click()
      await expectActionable(page.getByRole('button', { name: /取消|Cancel/ }).last())
      const deckOption = page.locator('label', { hasText: 'Responsive Deck' }).last()
      const deckCheckbox = page.getByLabel('Responsive Deck')
      await expectActionable(deckOption)
      await deckOption.click()
      await expect(deckCheckbox).toBeChecked()
      await expectActionable(page.getByRole('button', { name: /确认|确定|安装|Install|Apply/ }).last())
      await expectNoHorizontalOverflow(page)
    })

    test('practice controls remain reachable after starting mode', async ({ page }) => {
      await gotoReady(page, '/deck/1/practice')
      const startButton = page.getByRole('button', { name: /智能复习|Smart review|Random|随机/ }).first()
      await expectActionable(startButton)
      await startButton.click()
      await expectNoHorizontalOverflow(page)
      const questionText = page.getByText(/ability to adapt to very small screens|layout should scroll instead of hiding actions/).first()
      await expect(questionText).toBeVisible()
      await revealPracticeAnswer(page)
      await expectActionable(page.getByRole('button', { name: /完全不会|Forgot/ }).first())
      await expectActionable(page.getByRole('button').last())
    })
  })
}
