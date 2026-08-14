import { expect, test } from '@playwright/test'

test('persons route shell renders', async ({ page }) => {
  await page.addInitScript(() => {
    window.sessionStorage.setItem(
      'admin-auth-token',
      JSON.stringify({
        token: 'test.jwt.token',
        expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        username: 'admin-test',
        role: 'ADMIN',
      }),
    )
  })

  await page.goto('/admin-ui/persons')
  await expect(page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Persons' })).toBeVisible()
  await expect(page.getByText('Local person records synced from source systems (Phase 1).')).toBeVisible()
})
