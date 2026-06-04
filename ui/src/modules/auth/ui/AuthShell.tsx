import type { PropsWithChildren } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { LogoMarkIcon } from '../../../shared/ui/icons'

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
        <div className="mb-6 flex items-center gap-3">
          <span className="inline-flex h-[34px] w-[34px] items-center justify-center rounded-md bg-[var(--color-brand)] text-white">
            <LogoMarkIcon className="h-5 w-5" />
          </span>
          <div>
            <div className="text-[15px] font-bold tracking-[-0.01em] text-[var(--color-text)]">
              {uiText.authShell.wordmark}
            </div>
            <div className="text-xs font-semibold text-[var(--color-text-muted)]">
              {uiText.authShell.wordmarkSub}
            </div>
          </div>
        </div>
        {children}
      </div>
    </div>
  )
}
