import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Input, PageHeader } from '../../../shared/ui'
import { routes } from '../../../shared/constants/routes'
import { HttpRequestError } from '../../../platform/adapters/http/httpJsonClient'
import { AuthClient } from '../data/authClient'
import { getToken, setToken } from '../state/tokenStore'

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
        setError('Invalid username or password.')
      } else {
        setError('Could not sign in. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-[60vh] w-full max-w-md flex-col justify-center px-4 py-10">
      <PageHeader
        title="Admin sign in"
        subtitle="Use your admin credentials to access the operations console."
      />
      <section className="mt-6 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-subtle)]">
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-[var(--color-text)]">Username</span>
            <Input
              autoComplete="username"
              autoFocus
              disabled={submitting}
              onChange={(event) => setUsername(event.target.value)}
              required
              value={username}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-[var(--color-text)]">Password</span>
            <Input
              autoComplete="current-password"
              disabled={submitting}
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>
          {error !== null && (
            <p
              role="alert"
              className="rounded-md border border-[var(--color-status-bad)] bg-[color-mix(in_srgb,var(--color-status-bad),white_85%)] px-3 py-2 text-sm text-[var(--color-status-bad)]"
            >
              {error}
            </p>
          )}
          <Button
            type="submit"
            disabled={submitting || username.trim().length === 0 || password.length === 0}
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </section>
    </div>
  )
}
