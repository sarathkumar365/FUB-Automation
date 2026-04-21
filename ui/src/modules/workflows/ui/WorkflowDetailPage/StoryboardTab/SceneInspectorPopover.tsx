/**
 * Floating inspector popover for the storyboard tab.
 *
 * Uses the shared Popover primitive (Radix under the hood) for accessible
 * focus management, escape-to-close, and click-outside-to-close. The
 * popover anchors to the selected scene via an invisible anchor element
 * placed at the scene's pixel coordinates inside the canvas wrapper. Side
 * choice is purely geometric — if the scene sits on the left half of the
 * canvas the popover flies out to the right, otherwise to the left — so
 * the card never clips off the edge.
 *
 * Outer popover content stays `overflow: visible` so chip shadows don't
 * clip; tall content scrolls inside a dedicated inner wrapper. The body
 * itself lives in `./inspector/InspectorBody` so this file stays focused
 * on popover positioning concerns.
 */
import type { CSSProperties } from 'react'
import { uiText } from '../../../../../shared/constants/uiText'
import { Button } from '../../../../../shared/ui/button'
import {
  Popover,
  PopoverAnchor,
  PopoverContent,
} from '../../../../../shared/ui/Popover'
import type { SceneLayout } from '../../../../workflows-builder/model/layoutEngine'
import type { Graph, GraphNode } from '../../../../workflows-builder/state/runtimeContract'
import {
  INSPECTOR_PADDING_X,
  INSPECTOR_PADDING_Y,
  POPOVER_EDGE_PADDING,
  POPOVER_MAX_HEIGHT,
  POPOVER_MIN_HEIGHT,
  POPOVER_OFFSET,
  POPOVER_WIDTH,
} from './constants'
import { InspectorBody } from './inspector/InspectorBody'

export interface SceneInspectorPopoverProps {
  graph: Graph
  sceneId: string
  sceneLayout: SceneLayout
  canvasWidth: number
  canvasHeight: number
  onClose: () => void
  /** Left edge of the SVG viewBox in user-space units. Scene coordinates are
   *  in user-space, so this offset converts them to pixel coordinates within
   *  the HTML container that layers the popover above the SVG. Defaults to 0
   *  for callers that still render with a zero-origin viewBox. */
  viewBoxOriginX?: number
}

export function SceneInspectorPopover({
  graph,
  sceneId,
  sceneLayout,
  canvasWidth,
  canvasHeight,
  onClose,
  viewBoxOriginX = 0,
}: SceneInspectorPopoverProps) {
  const node: GraphNode | undefined = graph.nodes.find((candidate) => candidate.id === sceneId)

  const scenePixelX = sceneLayout.x - viewBoxOriginX
  const side: 'left' | 'right' = scenePixelX <= canvasWidth / 2 ? 'right' : 'left'
  const sceneTop = sceneLayout.y - sceneLayout.height / 2
  const maxHeight = Math.max(
    POPOVER_MIN_HEIGHT,
    Math.min(canvasHeight - POPOVER_EDGE_PADDING, POPOVER_MAX_HEIGHT),
  )

  const anchorStyle: CSSProperties = {
    position: 'absolute',
    left: scenePixelX - sceneLayout.width / 2,
    top: sceneTop,
    width: sceneLayout.width,
    height: sceneLayout.height,
    pointerEvents: 'none',
  }

  const contentStyle: CSSProperties = {
    width: POPOVER_WIDTH,
    maxHeight,
    // The outer card keeps shadows/chips visible; an inner wrapper handles
    // scroll when content overflows. Clipping the outer card caused chip
    // rows to be cut off by the border.
    overflow: 'visible',
  }

  return (
    <Popover
      open
      onOpenChange={(next) => {
        if (!next) onClose()
      }}
    >
      <PopoverAnchor asChild>
        <div aria-hidden="true" style={anchorStyle} />
      </PopoverAnchor>
      <PopoverContent
        side={side}
        align="start"
        sideOffset={POPOVER_OFFSET}
        data-testid="workflow-scene-inspector"
        data-popover-side={side}
        aria-label={uiText.workflows.sceneInspectorTitle}
        style={contentStyle}
        onOpenAutoFocus={(event) => {
          // Keep focus on the popover root but prevent auto-scrolling that
          // Radix performs when focusing the first focusable element.
          event.preventDefault()
        }}
      >
        {node ? (
          <InspectorBody node={node} maxHeight={maxHeight} onClose={onClose} />
        ) : (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              padding: `${INSPECTOR_PADDING_Y}px ${INSPECTOR_PADDING_X}px`,
            }}
          >
            <span className="text-sm text-[var(--color-text-muted)]">
              {uiText.workflows.sceneInspectorNotFound}
            </span>
            <Button type="button" size="sm" variant="ghost" onClick={onClose}>
              {uiText.workflows.sceneInspectorCloseLabel}
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}
