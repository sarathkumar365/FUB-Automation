/* Token-driven SVG chart primitives for the dashboard exploration.
   Calm by design: hairline axes, soft gradient fills, mono value labels.
   No chart library; every color flows from a CSS variable. */
const { useId: useChartId } = React;

/* Catmull-Rom → cubic bezier, for a smooth (not wobbly) line through points. */
function smoothPath(pts) {
  if (pts.length < 2) return '';
  let d = `M ${pts[0].x} ${pts[0].y}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] || p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = p1.y + (p2.y - p0.y) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C ${c1x} ${c1y} ${c2x} ${c2y} ${p2.x} ${p2.y}`;
  }
  return d;
}

/* ---- AreaChart — smooth area + line, faint baseline, last-point marker --- */
function AreaChart({ data, width = 640, height = 170, color = 'var(--color-brand)', pad = 8, showDot = true, baseline = true, strokeWidth = 2.4 }) {
  const gid = useChartId().replace(/:/g, '');
  const max = Math.max(...data) * 1.12;
  const min = Math.min(...data) * 0.86;
  const span = max - min || 1;
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;
  const pts = data.map((v, i) => ({
    x: pad + (i / (data.length - 1)) * innerW,
    y: pad + innerH - ((v - min) / span) * innerH,
  }));
  const line = smoothPath(pts);
  const area = `${line} L ${pts[pts.length - 1].x} ${pad + innerH} L ${pts[0].x} ${pad + innerH} Z`;
  const last = pts[pts.length - 1];
  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none" style={{ display: 'block', overflow: 'visible' }}>
      <defs>
        <linearGradient id={`ag-${gid}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.22" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      {baseline ? <line x1={pad} y1={pad + innerH} x2={width - pad} y2={pad + innerH} stroke="var(--color-border)" strokeWidth="1" /> : null}
      <path d={area} fill={`url(#ag-${gid})`} />
      <path d={line} fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      {showDot ? (
        <g>
          <circle cx={last.x} cy={last.y} r="5" fill="var(--color-surface)" stroke={color} strokeWidth="2.4" />
        </g>
      ) : null}
    </svg>
  );
}

/* ---- Sparkbars — compact vertical bars; last N highlighted -------------- */
function Sparkbars({ data, height = 40, color = 'var(--color-brand)', highlight = 2, gap = 3, radius = 2 }) {
  const max = Math.max(...data) || 1;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap, height }}>
      {data.map((v, i) => (
        <span key={i} style={{
          flex: 1, minHeight: 2, height: `${(v / max) * 100}%`, borderRadius: radius,
          background: i >= data.length - highlight ? color : `color-mix(in srgb, ${color} 30%, transparent)`,
        }} />
      ))}
    </div>
  );
}

/* ---- Sparkline — tiny inline smooth line -------------------------------- */
function Sparkline({ data, width = 120, height = 34, color = 'var(--color-brand)' }) {
  const max = Math.max(...data) * 1.1, min = Math.min(...data) * 0.9, span = max - min || 1;
  const pts = data.map((v, i) => ({ x: (i / (data.length - 1)) * width, y: height - ((v - min) / span) * (height - 4) - 2 }));
  return (
    <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} style={{ display: 'block', overflow: 'visible' }}>
      <path d={smoothPath(pts)} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      <circle cx={pts[pts.length - 1].x} cy={pts[pts.length - 1].y} r="2.6" fill={color} />
    </svg>
  );
}

/* ---- Donut — segmented ring; renders center children -------------------- */
function Donut({ segments, size = 168, thickness = 18, gap = 3, children }) {
  const r = (size - thickness) / 2;
  const c = 2 * Math.PI * r;
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  let offset = 0;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--color-surface-alt)" strokeWidth={thickness} />
        {segments.map((s, i) => {
          const frac = s.value / total;
          const len = Math.max(frac * c - gap, 0);
          const dash = `${len} ${c - len}`;
          const el = (
            <circle key={i} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness}
              strokeDasharray={dash} strokeDashoffset={-offset} strokeLinecap="round" />
          );
          offset += frac * c;
          return el;
        })}
      </svg>
      {children ? <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>{children}</div> : null}
    </div>
  );
}

/* ---- HBars — labeled horizontal bars with value -------------------------- */
function HBars({ rows, color = 'var(--color-brand)' }) {
  const max = Math.max(...rows.map((r) => r.value)) || 1;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {rows.map((r) => (
        <div key={r.label} style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center' }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 5 }}>
              <span className="mono" style={{ fontSize: 12, color: 'var(--color-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.label}</span>
              <span className="mono ae-tnum" style={{ fontSize: 12, color: 'var(--color-text-muted)', marginLeft: 8 }}>{r.value}{r.suffix || ''}</span>
            </div>
            <div style={{ height: 7, borderRadius: 999, background: 'var(--color-surface-alt)', overflow: 'hidden' }}>
              <div style={{ width: `${(r.value / max) * 100}%`, height: '100%', borderRadius: 999, background: r.color || color }} />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ---- DonutLegend row ---------------------------------------------------- */
function LegendRow({ color, label, value, sub }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
      <span style={{ width: 9, height: 9, borderRadius: 3, background: color, flexShrink: 0 }} />
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text)' }}>{label}</span>
      <span className="mono ae-tnum" style={{ marginLeft: 'auto', fontSize: 12.5, fontWeight: 600, color: 'var(--color-text)' }}>{value}</span>
      {sub ? <span style={{ fontSize: 11, color: 'var(--color-text-muted)', width: 40, textAlign: 'right' }}>{sub}</span> : null}
    </div>
  );
}

Object.assign(window, { smoothPath, AreaChart, Sparkbars, Sparkline, Donut, HBars, LegendRow });
