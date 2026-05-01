/**
 * Shadcn-style wrapper around `@radix-ui/react-popover`.
 *
 * Provides accessible popover semantics (focus management, escape-to-close,
 * click-outside-to-close, portaling) out of the box. Styled via Tailwind +
 * our CSS variables so the primitive matches the rest of the design system.
 */
import * as PopoverPrimitive from '@radix-ui/react-popover'
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react'
import { cn } from '../lib/cn'

export function Popover(props: ComponentPropsWithoutRef<typeof PopoverPrimitive.Root>) {
  return <PopoverPrimitive.Root {...props} />
}

export const PopoverTrigger = forwardRef<
  ElementRef<typeof PopoverPrimitive.Trigger>,
  ComponentPropsWithoutRef<typeof PopoverPrimitive.Trigger>
>(function PopoverTrigger(props, ref) {
  return <PopoverPrimitive.Trigger ref={ref} {...props} />
})

export const PopoverAnchor = forwardRef<
  ElementRef<typeof PopoverPrimitive.Anchor>,
  ComponentPropsWithoutRef<typeof PopoverPrimitive.Anchor>
>(function PopoverAnchor(props, ref) {
  return <PopoverPrimitive.Anchor ref={ref} {...props} />
})

export const PopoverContent = forwardRef<
  ElementRef<typeof PopoverPrimitive.Content>,
  ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(function PopoverContent({ className, align = 'center', sideOffset = 4, ...props }, ref) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        ref={ref}
        align={align}
        sideOffset={sideOffset}
        className={cn(
          'z-50 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[0_16px_40px_rgba(15,23,42,0.14)] outline-none',
          'focus-visible:outline-none',
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  )
})
