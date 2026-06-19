import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { SignupPage } from '@modules/auth/ui/SignupPage'
import { uiText } from '@shared/constants/uiText'

function renderSignup() {
  return render(
    <MemoryRouter initialEntries={['/admin-ui/signup']}>
      <Routes>
        <Route path="/admin-ui/signup" element={<SignupPage />} />
        <Route path="/admin-ui/login" element={<div>login-page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SignupPage', () => {
  it('renders the request-account form with the brand wordmark', () => {
    renderSignup()
    expect(screen.getByRole('heading', { name: uiText.signup.title })).toBeInTheDocument()
    expect(screen.getByText(uiText.authShell.wordmark)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.signup.fullNameLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.signup.emailLabel)).toBeInTheDocument()
  })

  it('blocks submit until every field is filled', () => {
    renderSignup()
    fireEvent.submit(screen.getByRole('button', { name: uiText.signup.submit }).closest('form')!)
    expect(screen.getByRole('alert').textContent).toBe(uiText.signup.errorRequired)
  })

  it('rejects mismatched passwords', () => {
    renderSignup()
    fireEvent.change(screen.getByLabelText(uiText.signup.fullNameLabel), { target: { value: 'Jordan Vega' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.emailLabel), { target: { value: 'a@b.com' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.passwordLabel), { target: { value: 'secret1' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.confirmLabel), { target: { value: 'secret2' } })
    fireEvent.submit(screen.getByRole('button', { name: uiText.signup.submit }).closest('form')!)
    expect(screen.getByRole('alert').textContent).toBe(uiText.signup.errorMismatch)
  })

  it('shows the phase-honest confirmation on a valid request', () => {
    renderSignup()
    fireEvent.change(screen.getByLabelText(uiText.signup.fullNameLabel), { target: { value: 'Jordan Vega' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.emailLabel), { target: { value: 'a@b.com' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.passwordLabel), { target: { value: 'secret1' } })
    fireEvent.change(screen.getByLabelText(uiText.signup.confirmLabel), { target: { value: 'secret1' } })
    fireEvent.submit(screen.getByRole('button', { name: uiText.signup.submit }).closest('form')!)
    expect(screen.getByRole('heading', { name: uiText.signup.confirmationTitle })).toBeInTheDocument()
    expect(screen.getByText(uiText.signup.backToSignIn)).toBeInTheDocument()
  })
})
