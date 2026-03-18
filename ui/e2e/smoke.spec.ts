import { expect, test } from '@playwright/test'

test('webhooks route shell renders', async ({ page }) => {
  await page.goto('/admin-ui/webhooks')
  await expect(page.getByText('Automation Engine Admin')).toBeVisible()
})
