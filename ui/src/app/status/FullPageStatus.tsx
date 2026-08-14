import type { PropsWithChildren } from 'react'
import { BrandLockup } from '@shared/ui/BrandLockup'

// Family signature gradient (handoff values — distinct from AuthShell's). Token
// only, so it themes in dark mode.
const AMBIENT_GRADIENT =
  'radial-gradient(760px 360px at 50% -14%, color-mix(in srgb, var(--color-brand) 13%, transparent), transparent 64%), ' +
  'radial-gradient(560px 300px at 86% 114%, color-mix(in srgb, var(--color-brand-2) 10%, transparent), transparent 60%), ' +
  'var(--color-bg)'

type FullPageStatusProps = PropsWithChildren<{
  inShell?: boolean
  hideLockup?: boolean
}>

/**
 * Shared shell for the system status screens (error · 404 · session-disabled).
 * Ambient brand gradient + top-centre brand lockup + a centred content slot.
 * `inShell` drops the gradient + lockup (the four-region shell already provides
 * branding and `--color-bg`). Router-hook-free so it can render from the
 * top-level class error boundary too.
 */
export function FullPageStatus({ inShell = false, hideLockup = false, children }: FullPageStatusProps) {
  return (
    <div
      className="relative isolate flex h-full min-h-[80vh] w-full flex-col overflow-hidden text-[var(--color-text)]"
      style={{ background: inShell ? 'transparent' : AMBIENT_GRADIENT }}
    >
      {!inShell && !hideLockup ? (
        <div className="absolute left-0 right-0 top-10 flex justify-center">
          <BrandLockup />
        </div>
      ) : null}
      <div
        className="flex flex-1 items-center justify-center"
        style={{ padding: inShell ? '32px' : '104px 40px 56px' }}
      >
        {children}
      </div>
    </div>
  )
}

