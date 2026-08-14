import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@shared/ui/button'
import { routes } from '@shared/constants/routes'
import { uiText } from '@shared/constants/uiText'
import { HttpRequestError } from '@platform/adapters/http/httpJsonClient'
import { AuthClient } from '../data/authClient'
import { getToken, setToken } from '../state/tokenStore'
import { AuthShell } from './AuthShell'
import { AuthError, AuthField } from './AuthField'

const ADMIN_PREFIX = '/admin-ui'

function safeNextPath(raw: string | null): string {
  if (raw === null || raw.trim().length === 0) return routes.dashboard
  // Only allow same-origin admin paths; ignore anything that tries to leave
  // the SPA (absolute URL, scheme-relative, ../ traversal, login itself).
  if (!raw.startsWith(ADMIN_PREFIX)) return routes.dashboard
  if (raw.startsWith(routes.login)) return routes.dashboard
  return raw
}

export function LoginPage({ authClient = new AuthClient() }: { authClient?: AuthClient } = {}) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNextPath(searchParams.get('next'))

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Already-logged-in users skip the login screen. Useful when the user
  // bookmarks /admin-ui/login or the auth guard sends a logged-in user here.
  useEffect(() => {
    if (getToken() !== null) {
      navigate(next, { replace: true })
    }
  }, [navigate, next])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setError(null)
    setSubmitting(true)
    try {
      const response = await authClient.login(username.trim(), password)
      setToken({
        token: response.token,
        expiresAt: response.expiresAt,
        username: response.username,
        role: response.role,
      })
      navigate(next, { replace: true })
    } catch (err) {
      if (err instanceof HttpRequestError && err.status === 401) {
        setError(uiText.login.errorInvalidCredentials)
      } else {
        setError(uiText.login.errorGeneric)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <h1 className="text-[22px] font-bold text-[var(--color-text)]">{uiText.login.title}</h1>
      <p className="mb-6 mt-1 text-sm text-[var(--color-text-muted)]">{uiText.login.subtitle}</p>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <AuthField
          label={uiText.login.usernameLabel}
          autoComplete="username"
          autoFocus
          disabled={submitting}
          onChange={(event) => setUsername(event.target.value)}
          required
          value={username}
        />
        <AuthField
          label={uiText.login.passwordLabel}
          autoComplete="current-password"
          disabled={submitting}
          onChange={(event) => setPassword(event.target.value)}
          required
          type="password"
          value={password}
        />
        {error !== null && <AuthError>{error}</AuthError>}
        <Button
          type="submit"
          disabled={submitting || username.trim().length === 0 || password.length === 0}
        >
          {submitting ? uiText.login.submitting : uiText.login.submit}
        </Button>
      </form>
      <p className="mt-5 text-sm text-[var(--color-text-muted)]">
        {uiText.login.requestAccountPrompt}{' '}
        <Link to={routes.signup} className="font-semibold text-[var(--color-brand)]">
          {uiText.login.requestAccountCta}
        </Link>
      </p>
    </AuthShell>
  )
}
