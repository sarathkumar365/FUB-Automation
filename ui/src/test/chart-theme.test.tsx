import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { useChartTheme } from '@platform/charts/chartTheme'

function Probe() {
  const tokens = useChartTheme(['--color-brand'] as const)
  return <output data-testid="probe">{tokens['--color-brand']}</output>
}

describe('useChartTheme', () => {
  afterEach(() => {
    document.documentElement.style.removeProperty('--color-brand')
    delete document.documentElement.dataset.theme
  })

  it('resolves CSS custom properties to concrete values', () => {
    document.documentElement.style.setProperty('--color-brand', 'teal')

    render(<Probe />)

    expect(screen.getByTestId('probe').textContent).toBe('teal')
  })

  it('re-resolves when the theme attribute flips', async () => {
    document.documentElement.style.setProperty('--color-brand', 'teal')
    render(<Probe />)

    document.documentElement.style.setProperty('--color-brand', 'tomato')
    document.documentElement.dataset.theme = 'dark'

    await waitFor(() => expect(screen.getByTestId('probe').textContent).toBe('tomato'))
  })
})
