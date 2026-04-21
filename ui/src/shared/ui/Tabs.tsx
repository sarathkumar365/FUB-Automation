/**
 * Shadcn-style wrapper around `@radix-ui/react-tabs`.
 *
 * Provides accessible tab semantics (role=tablist, role=tab, arrow-key
 * navigation, focus management) out of the box. Styled via Tailwind + our
 * CSS variables so the primitive matches the rest of the design system.
 */
import * as TabsPrimitive from '@radix-ui/react-tabs'
import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react'
import { cn } from '../lib/cn'

export function Tabs(props: ComponentPropsWithoutRef<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root {...props} />
}

export const TabsList = forwardRef<
  ElementRef<typeof TabsPrimitive.List>,
  ComponentPropsWithoutRef<typeof TabsPrimitive.List>
>(function TabsList({ className, ...props }, ref) {
  return (
    <TabsPrimitive.List
      ref={ref}
      className={cn(
        'flex items-center gap-6 border-b border-[var(--color-border)]',
        className,
      )}
      {...props}
    />
  )
})

export const TabsTrigger = forwardRef<
  ElementRef<typeof TabsPrimitive.Trigger>,
  ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>
>(function TabsTrigger({ className, ...props }, ref) {
  return (
    <TabsPrimitive.Trigger
      ref={ref}
      className={cn(
        '-mb-px border-b-2 border-transparent px-1 py-2 text-sm font-medium text-[var(--color-text-muted)] transition-colors',
        'hover:text-[var(--color-text)]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]',
        'data-[state=active]:border-[var(--color-brand)] data-[state=active]:text-[var(--color-text)] data-[state=active]:font-semibold',
        className,
      )}
      {...props}
    />
  )
})

export const TabsContent = forwardRef<
  ElementRef<typeof TabsPrimitive.Content>,
  ComponentPropsWithoutRef<typeof TabsPrimitive.Content>
>(function TabsContent({ className, ...props }, ref) {
  return (
    <TabsPrimitive.Content
      ref={ref}
      className={cn(
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]',
        className,
      )}
      {...props}
    />
  )
})
