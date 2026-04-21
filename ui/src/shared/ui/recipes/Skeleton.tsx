/**
 * Loading-state placeholder shapes.
 *
 * Per D3.6-d (workflow-builder-ui-audit, Slice 3): ships with `line` and
 * `block` shapes only. Additional shapes (`circle`, `table-row`, etc.) are
 * added on demand when a real screen needs one — don't pre-optimize.
 *
 * Uses `--color-surface-alt` as the base tone so skeletons read as "about to
 * become content" against the app's `--color-surface` cards. No animation
 * primitive yet; if needed later, layer a `shimmer` variant on top of this
 * component rather than inlining `@keyframes` everywhere.
 */
import type { HTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/cn'

const skeletonVariants = cva(
  'block animate-pulse rounded-md bg-[var(--color-surface-alt)] border border-[var(--color-border)]',
  {
    variants: {
      shape: {
        line: 'h-3 w-full rounded-full',
        block: 'h-24 w-full',
      },
    },
    defaultVariants: {
      shape: 'line',
    },
  },
)

export interface SkeletonProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof skeletonVariants> {}

export function Skeleton({ className, shape, ...props }: SkeletonProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      className={cn(skeletonVariants({ shape }), className)}
      {...props}
    />
  )
}
