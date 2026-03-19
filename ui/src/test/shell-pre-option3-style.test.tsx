import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('pre-Option 3 shell style', () => {
  it('uses the standard light rail surface and no mesh classes', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    const rail = await screen.findByLabelText(uiText.app.shell.railAriaLabel)
    const shellRow = rail.parentElement

    expect(rail.className).toContain('bg-[var(--color-surface)]')
    expect(rail.className).not.toContain('shell-mesh')
    expect(shellRow?.className).toContain('w-full')
    expect(shellRow?.className).not.toContain('max-w-[1500px]')
  })
})
