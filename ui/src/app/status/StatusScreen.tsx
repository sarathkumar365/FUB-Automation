import type { ComponentType, ReactNode, SVGProps } from 'react'
import { useRise } from '@shared/lib/useRise'
import { Button } from '@shared/ui'

type Tone = 'bad' | 'warn' | 'brand'

const TONES: Record<Tone, { fg: string; bg: string }> = {
  bad: { fg: 'var(--color-status-bad)', bg: 'var(--color-status-bad-bg)' },
  warn: { fg: 'var(--color-status-warn)', bg: 'var(--color-status-warn-bg)' },
  brand: { fg: 'var(--color-brand)', bg: 'var(--color-brand-soft)' },
}

type Glyph = ComponentType<SVGProps<SVGSVGElement>>

export type StatusContent = {
  tone: Tone
  glyph: Glyph
  eyebrow: string
  title: string
  body: string
  helper?: ReactNode
  meta: string
  /** Overrides the default ghost-glyph watermark (e.g. the "404" numerals). */
  watermark?: ReactNode
  primary?: { label: string; onClick: () => void }
  secondary?: { label: string; href?: string; onClick?: () => void; icon?: ReactNode }
  extra?: ReactNode
}

export function StatusScreen({
  tone,
  glyph: Glyph,
  eyebrow,
  title,
  body,
  helper,
  meta,
  watermark,
  primary,
  secondary,
  extra,
}: StatusContent) {
  const t = TONES[tone]
  const rise = useRise<HTMLDivElement>()

  return (
    <div className="relative flex w-full max-w-[560px] justify-center">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 leading-none"
        style={{ transform: 'translate(-50%, -56%)', color: t.fg, opacity: 0.07 }}
      >
        {watermark ?? <Glyph className="h-[372px] w-[372px]" strokeWidth={1.1} />}
      </div>

      <div ref={rise} className="relative flex flex-col items-center text-center">
        <span
          className="mb-5 inline-flex items-center gap-2 rounded-full px-[13px] py-1.5 text-[11px] font-extrabold uppercase tracking-[0.13em]"
          style={{ background: t.bg, color: t.fg }}
        >
          <Glyph className="h-3.5 w-3.5" strokeWidth={2} />
          {eyebrow}
        </span>

        <h1 className="text-[32px] font-extrabold leading-[1.1] tracking-[-0.025em] text-[var(--color-text)]">{title}</h1>
        <p className="mt-3.5 max-w-[42ch] text-pretty text-[15px] leading-[1.55] text-[var(--color-text-muted)]">{body}</p>
        {helper ? <div className="mt-3.5 text-[13.5px] leading-[1.5] text-[var(--color-text-muted)]">{helper}</div> : null}

        <div className="mt-5 inline-flex items-center gap-2.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2.5 shadow-[var(--shadow-subtle)]">
          <span className="h-[7px] w-[7px] rounded-full" style={{ background: t.fg }} />
          <span className="whitespace-nowrap font-mono text-xs text-[var(--color-text)]">{meta}</span>
        </div>

        {primary || secondary ? (
          <div className="mt-6 flex flex-wrap items-center justify-center gap-4">
            {primary ? (
              <Button size="lg" onClick={primary.onClick}>
                {primary.label}
              </Button>
            ) : null}
            {secondary ? <SecondaryAction {...secondary} /> : null}
          </div>
        ) : null}

        {extra}
      </div>
    </div>
  )
}

function SecondaryAction({ label, href, onClick, icon }: NonNullable<StatusContent['secondary']>) {
  const className =
    'inline-flex items-center gap-1.5 text-sm font-semibold text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-text)]'
  if (href) {
    return (
      <a href={href} onClick={onClick} className={className}>
        {icon}
        {label}
      </a>
    )
  }
  return (
    <button type="button" onClick={onClick} className={className}>
      {icon}
      {label}
    </button>
  )
}
