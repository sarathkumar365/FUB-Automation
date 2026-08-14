import { useEffect, useRef } from 'react'

// Transform-only entrance rise (never opacity) so a throttled/background tab
// can't strand content hidden — worst case it rests a few px low. Skipped under
// prefers-reduced-motion. Attach the returned ref to the element to animate.
export function useRise<T extends HTMLElement = HTMLDivElement>(delay = 0) {
  const ref = useRef<T>(null)

  useEffect(() => {
    const el = ref.current
    if (!el || typeof el.animate !== 'function') return
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches) return
    try {
      el.animate([{ transform: 'translateY(14px)' }, { transform: 'translateY(0)' }], {
        duration: 540,
        delay,
        easing: 'cubic-bezier(.2,.7,.3,1)',
      })
    } catch {
      // The CSS resting state is already visible; a failed animation is harmless.
    }
  }, [delay])

  return ref
}
