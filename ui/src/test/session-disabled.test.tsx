import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SessionDisabledPage } from '../app/SessionDisabledPage'
import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'

describe('SessionDisabledPage', () => {
  it('renders the warn status screen with title, body, strip and helper', () => {
    render(<SessionDisabledPage />)

    expect(screen.getByRole('heading', { name: uiText.session.title })).toBeInTheDocument()
    expect(screen.getByText(uiText.session.body)).toBeInTheDocument()
    expect(screen.getByText(uiText.session.strip)).toBeInTheDocument()
    // The helper emphasizes "workspace administrator".
    expect(screen.getByText(uiText.session.helperEmphasis).className).toContain('font-semibold')
  })

  it('offers only a quiet "return to sign in" link — no primary action', () => {
    render(<SessionDisabledPage />)

    expect(screen.getByRole('link', { name: uiText.session.returnToSignIn })).toHaveAttribute(
      'href',
      routes.login,
    )
    // No primary button — there may be nothing the user can do from here.
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
