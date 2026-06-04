import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../../shared/ui'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { AuthShell } from './AuthShell'
import { AuthError, AuthField } from './AuthField'

/**
 * Request-an-account screen. The product is admin-provisioned and there is no
 * request-account backend endpoint yet, so the form is UI-only: it validates
 * client-side and then shows a phase-honest confirmation directing the user to
 * an administrator. Wire to a backend endpoint when one exists.
 */
export function SignupPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    if (!fullName.trim() || !email.trim() || !password || !confirm) {
      setError(uiText.signup.errorRequired)
      return
    }
    if (password !== confirm) {
      setError(uiText.signup.errorMismatch)
      return
    }
    setSubmitted(true)
  }

  if (submitted) {
    return (
      <AuthShell>
        <h1 className="text-[22px] font-bold text-[var(--color-text)]">
          {uiText.signup.confirmationTitle}
        </h1>
        <p className="mb-6 mt-2 text-sm text-[var(--color-text-muted)]">
          {uiText.signup.confirmationBody}
        </p>
        <Link to={routes.login} className="text-sm font-semibold text-[var(--color-brand)]">
          {uiText.signup.backToSignIn}
        </Link>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1 className="text-[22px] font-bold text-[var(--color-text)]">{uiText.signup.title}</h1>
      <p className="mb-6 mt-1 text-sm text-[var(--color-text-muted)]">{uiText.signup.subtitle}</p>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <AuthField
          label={uiText.signup.fullNameLabel}
          autoFocus
          onChange={(event) => setFullName(event.target.value)}
          placeholder={uiText.signup.fullNamePlaceholder}
          required
          value={fullName}
        />
        <AuthField
          label={uiText.signup.emailLabel}
          autoComplete="email"
          onChange={(event) => setEmail(event.target.value)}
          placeholder={uiText.signup.emailPlaceholder}
          required
          type="email"
          value={email}
        />
        <div className="flex gap-3">
          <div className="flex-1">
            <AuthField
              label={uiText.signup.passwordLabel}
              autoComplete="new-password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </div>
          <div className="flex-1">
            <AuthField
              label={uiText.signup.confirmLabel}
              autoComplete="new-password"
              onChange={(event) => setConfirm(event.target.value)}
              required
              type="password"
              value={confirm}
            />
          </div>
        </div>
        {error !== null && <AuthError>{error}</AuthError>}
        <Button type="submit">{uiText.signup.submit}</Button>
      </form>
      <p className="mt-5 text-sm text-[var(--color-text-muted)]">
        {uiText.signup.haveAccountPrompt}{' '}
        <Link to={routes.login} className="font-semibold text-[var(--color-brand)]">
          {uiText.signup.signInCta}
        </Link>
      </p>
    </AuthShell>
  )
}
