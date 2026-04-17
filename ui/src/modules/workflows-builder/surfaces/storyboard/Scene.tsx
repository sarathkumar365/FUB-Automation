/**
 * A single scene card on the storyboard.
 *
 * Scene rendering is read-only in Phase 1. Click selection flows through the
 * builder store so the debug overlay can record it as an action.
 *
 * We render as foreignObject inside the Storyboard SVG so cards can use real
 * HTML/CSS (text wrapping, font metrics) while still living in the same
 * coordinate space as the spine + edges. Every interactive element has
 * `data-builder-region` so Playwright tests and the debug overlay can find
 * them without brittle CSS selectors.
 */
import { formatScene, type FormatterAccent } from '../../model/cardFormatters'
import type { SceneLayout } from '../../model/layoutEngine'
import type { StoryboardScene } from '../../model/graphAdapters'

const ACCENT_TOKENS: Record<FormatterAccent, { border: string; chip: string; text: string }> = {
  trigger: { border: '#0f9fb8', chip: 'rgba(15, 159, 184, 0.18)', text: '#0f9fb8' },
  'side-effect': { border: '#d97706', chip: 'rgba(217, 119, 6, 0.14)', text: '#b45309' },
  wait: { border: '#6366f1', chip: 'rgba(99, 102, 241, 0.14)', text: '#4338ca' },
  branch: { border: '#db2777', chip: 'rgba(219, 39, 119, 0.14)', text: '#9d174d' },
  compute: { border: '#059669', chip: 'rgba(5, 150, 105, 0.14)', text: '#047857' },
  neutral: { border: '#64748b', chip: 'rgba(100, 116, 139, 0.14)', text: '#334155' },
}

export interface SceneProps {
  scene: StoryboardScene
  layout: SceneLayout
  selected: boolean
  onSelect: (sceneId: string) => void
}

export function Scene({ scene, layout, selected, onSelect }: SceneProps) {
  const formatted = formatScene(scene.stepType, scene.config)
  const accent = ACCENT_TOKENS[formatted.accent]
  const left = layout.x - layout.width / 2
  const top = layout.y - layout.height / 2

  return (
    <foreignObject x={left} y={top} width={layout.width} height={layout.height}>
      <div
        // xmlns is required for foreignObject HTML children
        // @ts-expect-error — xmlns attribute is valid on divs inside foreignObject
        xmlns="http://www.w3.org/1999/xhtml"
        data-builder-region="scene"
        data-scene-id={scene.id}
        data-step-type={scene.stepType}
        data-accent={formatted.accent}
        role="button"
        tabIndex={0}
        onClick={() => onSelect(scene.id)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            onSelect(scene.id)
          }
        }}
        style={{
          boxSizing: 'border-box',
          width: '100%',
          height: '100%',
          padding: '12px 14px',
          borderRadius: 12,
          background: '#ffffff',
          border: `1.5px solid ${selected ? accent.border : 'rgba(15, 23, 42, 0.12)'}`,
          boxShadow: selected
            ? `0 0 0 3px ${accent.chip}, 0 8px 24px rgba(15, 23, 42, 0.08)`
            : '0 2px 10px rgba(15, 23, 42, 0.06)',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          fontFamily: 'Manrope, system-ui, sans-serif',
        }}
      >
        <div
          style={{
            display: 'inline-flex',
            alignSelf: 'flex-start',
            alignItems: 'center',
            gap: 6,
            padding: '2px 8px',
            borderRadius: 999,
            background: accent.chip,
            color: accent.text,
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.3,
            textTransform: 'uppercase',
          }}
        >
          {scene.isEntry ? 'entry · ' : ''}
          {scene.stepType}
        </div>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#0f172a' }}>{formatted.title}</div>
        <div style={{ fontSize: 12, color: '#475569', fontFamily: 'JetBrains Mono, monospace' }}>
          {formatted.summary}
        </div>
        <div style={{ marginTop: 'auto', fontSize: 11, color: '#94a3b8' }}>{scene.id}</div>
      </div>
    </foreignObject>
  )
}
