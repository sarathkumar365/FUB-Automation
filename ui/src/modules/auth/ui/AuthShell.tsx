import type { PropsWithChildren } from 'react'
import { BrandLockup } from '../../../shared/ui/BrandLockup'

/**
 * Shared auth-screen layout (login + signup). A calm centered card on an
 * ambient brand gradient, led by the flow-nodes LogoMark + product wordmark —
 * the design system's auth "spine".
 */
export function AuthShell({ children }: PropsWithChildren) {
  return (
    <div
      className="flex min-h-[80vh] w-full items-center justify-center px-4 py-10"
      style={{
        background:
          'radial-gradient(700px 320px at 50% -10%, color-mix(in srgb, var(--color-brand) 12%, transparent), transparent 64%), radial-gradient(520px 260px at 88% 108%, color-mix(in srgb, var(--color-brand-2) 9%, transparent), transparent 60%)',
      }}
    >
      <div className="w-[min(400px,100%)] rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-7 shadow-[var(--shadow-float)]">
        <BrandLockup className="mb-6" />
        {children}
      </div>
    </div>
  )
}
