/**
 * Pure helpers for vertical exit-edge rendering.
 *
 * Label anti-overlap: when multiple edges leave the same scene (e.g. success /
 * failure from a branch), we spread their labels along the bezier so the chips
 * don't stack on top of each other. `edgeLabelT` returns a fractional t in
 * [0.3, 0.7] that is distinct for distinct (index, count) pairs.
 */

/**
 * Fraction along the bezier where an edge label should be anchored.
 *
 * For n edges sharing a parent, indices 0..n-1 are placed evenly between
 * 30% and 70% of the curve so chips don't stack. When there is only one
 * edge the label sits at 50%.
 */
export function edgeLabelT(edgeIndex: number, edgeCount: number): number {
  const count = Math.max(edgeCount, 1)
  if (count <= 1) return 0.5
  const step = 0.4 / (count - 1)
  return 0.3 + edgeIndex * step
}

/** Cubic bezier point at fraction t given start, two control points, and end. */
export function cubicBezierPoint(
  t: number,
  p0: { x: number; y: number },
  p1: { x: number; y: number },
  p2: { x: number; y: number },
  p3: { x: number; y: number },
): { x: number; y: number } {
  const mt = 1 - t
  const x = mt * mt * mt * p0.x + 3 * mt * mt * t * p1.x + 3 * mt * t * t * p2.x + t * t * t * p3.x
  const y = mt * mt * mt * p0.y + 3 * mt * mt * t * p1.y + 3 * mt * t * t * p2.y + t * t * t * p3.y
  return { x, y }
}
