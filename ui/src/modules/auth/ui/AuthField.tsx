import type { ComponentProps, ReactNode } from 'react'
import { Input } from '../../../shared/ui/input'

/**
 * Auth-screen field + error helpers, shared by LoginPage and SignupPage so the
 * uppercase caption + input layout and the error treatment are defined once.
 */
const LABEL_CLASS =
  'text-[11px] font-semibold uppercase tracking-[0.04em] text-[var(--color-text-muted)]'

export function AuthField({
  label,
  ...inputProps
}: { label: string } & ComponentProps<typeof Input>) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className={LABEL_CLASS}>{label}</span>
      <Input {...inputProps} />
    </label>
  )
}

export function AuthError({ children }: { children: ReactNode }) {
  return (
    <p
      role="alert"
      className="rounded-md border border-[var(--color-status-bad)] bg-[var(--color-status-bad-bg)] px-3 py-2 text-sm text-[var(--color-status-bad)]"
    >
      {children}
    </p>
  )
}
