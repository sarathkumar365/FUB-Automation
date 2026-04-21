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
import { getAccentTone } from './accentTokens'

// Scene cards render neutral by default: no colored left stripe, no accent-tinted
// pill. The `data-accent` attribute still reflects the step-type category so
// tests and later features can read it, but the visible treatment is monochrome
// so the graph reads calmly at scan-speed. All colors below come from
// `ui/src/styles/tokens.css` — no hardcoded literals here.

export interface SceneProps {
  scene: StoryboardScene
  layout: SceneLayout
  selected: boolean
  onSelect: (sceneId: string) => void
}

export function Scene({ scene, layout, selected, onSelect }: SceneProps) {
  const formatted = formatScene(scene.stepType, scene.config)
  const tone = getAccentTone(formatted.accent)
  const left = layout.x - layout.width / 2
  const top = layout.y - layout.height / 2
  const isTrigger = scene.stepType === '__trigger__'
  const tooltip = isTrigger ? 'trigger' : scene.stepType
  const pillLabel = isTrigger ? 'trigger' : `${scene.isEntry ? 'entry · ' : ''}${scene.stepType}`

  return (
    <foreignObject x={left} y={top} width={layout.width} height={layout.height}>
      <div
        className="storyboard-scene-card"
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
          background: 'var(--color-surface)',
          border: selected
            ? `2px solid var(--color-storyboard-card-ring-selected)`
            : `1.5px solid var(--color-storyboard-card-border)`,
          boxShadow: selected
            ? 'var(--color-storyboard-card-shadow-selected)'
            : 'var(--color-storyboard-card-shadow)',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 6,
          fontFamily: 'var(--font-ui)',
          minWidth: 0,
        }}
      >
        <span
          data-builder-region="scene-type"
          // Tone currently resolves to neutral for every scene — see the comment
          // at the top of the file. The accent tokens are still wired up so we
          // can opt into colored categories per scene in a later pass without
          // re-plumbing color resolution.
          data-accent-fg={tone.fg}
          style={{
            alignSelf: 'flex-start',
            maxWidth: '100%',
            display: 'inline-flex',
            alignItems: 'center',
            padding: '1px 8px',
            borderRadius: 999,
            background: 'var(--color-accent-neutral-bg)',
            color: 'var(--color-accent-neutral-fg)',
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: 0.2,
            fontFamily: 'var(--font-mono)',
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
            color: 'var(--color-text)',
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
