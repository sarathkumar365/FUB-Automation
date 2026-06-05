import { uiText } from '../constants/uiText'
import { cn } from '../lib/cn'
import { LogoMarkIcon } from './icons'

// Brand square + product wordmark — the design system's lockup, shared by the
// auth shell and the full-page status screens. Single source for the wordmark
// (RD-005 rename stays one place).
export function BrandLockup({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <span className="inline-flex h-[34px] w-[34px] items-center justify-center rounded-md bg-[var(--color-brand)] text-white">
        <LogoMarkIcon className="h-5 w-5" />
      </span>
      <div className="text-left">
        <div className="text-[15px] font-bold tracking-[-0.01em] text-[var(--color-text)]">
          {uiText.authShell.wordmark}
        </div>
        <div className="text-xs font-semibold text-[var(--color-text-muted)]">{uiText.authShell.wordmarkSub}</div>
      </div>
    </div>
  )
}
