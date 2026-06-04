import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, Input } from '../../../shared/ui'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { AuthShell } from './AuthShell'

const FIELD_LABEL_CLASS =
  'text-[11px] font-semibold uppercase tracking-[0.04em] text-[var(--color-text-muted)]'

const ERROR_CLASS =
  'rounded-md border border-[var(--color-status-bad)] bg-[color-mix(in_srgb,var(--color-status-bad),transparent_88%)] px-3 py-2 text-sm text-[var(--color-status-bad)]'

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
        <label className="flex flex-col gap-1.5">
          <span className={FIELD_LABEL_CLASS}>{uiText.signup.fullNameLabel}</span>
          <Input
            autoFocus
            onChange={(event) => setFullName(event.target.value)}
            placeholder={uiText.signup.fullNamePlaceholder}
            required
            value={fullName}
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className={FIELD_LABEL_CLASS}>{uiText.signup.emailLabel}</span>
          <Input
            autoComplete="email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder={uiText.signup.emailPlaceholder}
            required
            type="email"
            value={email}
          />
        </label>
        <div className="flex gap-3">
          <label className="flex flex-1 flex-col gap-1.5">
            <span className={FIELD_LABEL_CLASS}>{uiText.signup.passwordLabel}</span>
            <Input
              autoComplete="new-password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>
          <label className="flex flex-1 flex-col gap-1.5">
            <span className={FIELD_LABEL_CLASS}>{uiText.signup.confirmLabel}</span>
            <Input
              autoComplete="new-password"
              onChange={(event) => setConfirm(event.target.value)}
              required
              type="password"
              value={confirm}
            />
          </label>
        </div>
        {error !== null && (
          <p role="alert" className={ERROR_CLASS}>
            {error}
          </p>
        )}
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
