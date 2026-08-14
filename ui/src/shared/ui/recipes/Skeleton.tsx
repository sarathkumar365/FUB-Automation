/**
 * Loading-state placeholder shapes.
 *
 * Per D3.6-d (workflow-builder-ui-audit, Slice 3): ships with `line` and
 * `block` shapes only. Additional shapes (`circle`, `table-row`, etc.) are
 * added on demand when a real screen needs one — don't pre-optimize.
 *
 * Uses `--color-surface-alt` as the base tone so skeletons read as "about to
 * become content" against the app's `--color-surface` cards. Two animations:
 * `pulse` (default) and `shimmer` (gradient slide, keyframes + tokens in
 * tokens.css — added for the reporting loading state).
 */
import type { HTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/cn'

const skeletonVariants = cva('block rounded-md', {
  variants: {
    shape: {
      line: 'h-3 w-full rounded-full',
      block: 'h-24 w-full',
    },
    animation: {
      pulse: 'animate-pulse bg-[var(--color-surface-alt)] border border-[var(--color-border)]',
      // gradient slide (`.skeleton-shimmer` in tokens.css); border-free per the
      // reporting handoff's loading bars
      shimmer: 'skeleton-shimmer',
    },
  },
  defaultVariants: {
    shape: 'line',
    animation: 'pulse',
  },
})

export interface SkeletonProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof skeletonVariants> {}

export function Skeleton({ className, shape, animation, ...props }: SkeletonProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      className={cn(skeletonVariants({ shape, animation }), className)}
      {...props}
    />
  )
}
