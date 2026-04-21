/**
 * A single scene card on the storyboard.
 *
 * Scene rendering is read-only in Phase 1. The card surfaces just enough to
 * scan at graph-speed:
 *   1. An accent-tinted step-type pill ("entry · set_variable") so category is
 *      legible without reading config.
 *   2. The formatted title ("Set variable x") as the primary copy.
 * Anything richer — node id, config, transitions — lives in the floating
 * inspector popover so the graph itself stays scannable. The summary/id lines
 * the card used to carry were redundant once the popover existed and bloated
 * the card height; they are intentionally gone here.
 *
 * We render as foreignObject inside the Storyboard SVG so cards can use real
 * HTML/CSS (truncation, font metrics) while still living in the same
 * coordinate space as the spine + edges. Every interactive element has
 * `data-builder-region` so Playwright tests and the debug overlay can find
 * them without brittle CSS selectors.
 */
import { formatScene } from '../../model/cardFormatters'
import type { SceneLayout } from '../../model/layoutEngine'
import type { StoryboardScene } from '../../model/graphAdapters'

// Scene cards render neutral: no colored left stripe, no accent-tinted pill.
// The `data-accent` attribute still reflects the step-type category so tests
// and later features can read it, but the visible treatment is monochrome so
// the graph reads calmly at scan-speed.
const NEUTRAL_PILL_BG = 'rgba(100, 116, 139, 0.1)'
const NEUTRAL_PILL_TEXT = '#475569'
const SELECTION_BORDER = 'var(--color-brand)'
const SELECTION_HALO = 'rgba(15, 23, 42, 0.12)'

export interface SceneProps {
  scene: StoryboardScene
  layout: SceneLayout
  selected: boolean
  onSelect: (sceneId: string) => void
}

export function Scene({ scene, layout, selected, onSelect }: SceneProps) {
  const formatted = formatScene(scene.stepType, scene.config)
  const left = layout.x - layout.width / 2
  const top = layout.y - layout.height / 2
  const isTrigger = scene.stepType === '__trigger__'
  const tooltip = isTrigger ? 'trigger' : scene.stepType
  const pillLabel = isTrigger ? 'trigger' : `${scene.isEntry ? 'entry · ' : ''}${scene.stepType}`

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
        title={tooltip}
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
          padding: '10px 14px',
          borderRadius: 12,
          background: '#ffffff',
          border: `1.5px solid ${selected ? SELECTION_BORDER : 'rgba(15, 23, 42, 0.12)'}`,
          boxShadow: selected
            ? `0 0 0 3px ${SELECTION_HALO}, 0 8px 24px rgba(15, 23, 42, 0.08)`
            : '0 2px 10px rgba(15, 23, 42, 0.06)',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 6,
          fontFamily: 'Manrope, system-ui, sans-serif',
          minWidth: 0,
        }}
      >
        <span
          data-builder-region="scene-type"
          style={{
            alignSelf: 'flex-start',
            maxWidth: '100%',
            display: 'inline-flex',
            alignItems: 'center',
            padding: '1px 8px',
            borderRadius: 999,
            background: NEUTRAL_PILL_BG,
            color: NEUTRAL_PILL_TEXT,
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.2,
            fontFamily: 'JetBrains Mono, ui-monospace, monospace',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {pillLabel}
        </span>
        <div
          data-builder-region="scene-name"
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: '#0f172a',
            lineHeight: 1.3,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            maxWidth: '100%',
          }}
        >
          {formatted.title}
        </div>
      </div>
    </foreignObject>
  )
}
