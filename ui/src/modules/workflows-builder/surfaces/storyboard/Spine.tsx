/**
 * The storyboard spine — a soft gradient line running left→right behind the
 * scenes, visually echoing the curved timeline on the landing page. Its only
 * job is to give the eye an "execution flows this way" cue. Positions are
 * computed from the layout so the spine matches wherever Dagre placed the
 * scenes.
 */
import type { StoryboardLayout } from '../../model/layoutEngine'

export interface SpineProps {
  layout: StoryboardLayout
}

export function Spine({ layout }: SpineProps) {
  if (layout.scenes.size === 0) return null
  const ys: number[] = []
  for (const scene of layout.scenes.values()) ys.push(scene.y)
  const avgY = ys.reduce((acc, y) => acc + y, 0) / ys.length
  const x1 = 12
  const x2 = layout.width - 12
  const midX = (x1 + x2) / 2
  const path = `M ${x1} ${avgY} Q ${midX} ${avgY - 24}, ${x2} ${avgY}`
  return (
    <g data-builder-region="spine" aria-hidden>
      <defs>
        <linearGradient id="storyboard-spine" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="rgba(15, 159, 184, 0.0)" />
          <stop offset="18%" stopColor="rgba(15, 159, 184, 0.35)" />
          <stop offset="82%" stopColor="rgba(15, 159, 184, 0.35)" />
          <stop offset="100%" stopColor="rgba(15, 159, 184, 0.0)" />
        </linearGradient>
      </defs>
      <path d={path} stroke="url(#storyboard-spine)" strokeWidth={3} fill="none" strokeLinecap="round" />
    </g>
  )
}
