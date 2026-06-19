/* Primitives — faithful cosmetic recreations of ui/src/shared/ui/*.
   Token-driven; no hard-coded hex. */
const { useState, useRef, useEffect } = React;

/* ---- Button (button.tsx variants) --------------------------------------- */
const BTN_BASE = {
  display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6,
  borderRadius: 'var(--radius-sm)', fontWeight: 500, fontFamily: 'var(--font-ui)',
  border: '1px solid transparent', whiteSpace: 'nowrap',
};
const BTN_SIZE = {
  default: { height: 36, padding: '0 16px', fontSize: 14 },
  sm:      { height: 32, padding: '0 12px', fontSize: 12 },
  lg:      { height: 40, padding: '0 32px', fontSize: 14 },
  icon:    { height: 36, width: 36, padding: 0 },
};
const BTN_VARIANT = {
  default:     { bg: 'var(--color-brand)', fg: '#fff', bd: 'transparent', hov: 'color-mix(in srgb, var(--color-brand), black 12%)' },
  secondary:   { bg: 'var(--color-brand-soft)', fg: 'var(--color-brand)', bd: 'transparent', hov: 'color-mix(in srgb, var(--color-brand-soft), black 6%)' },
  outline:     { bg: 'var(--color-surface)', fg: 'var(--color-text)', bd: 'var(--color-border)', hov: 'var(--color-surface-alt)' },
  ghost:       { bg: 'transparent', fg: 'var(--color-text)', bd: 'transparent', hov: 'var(--color-surface-alt)' },
  destructive: { bg: 'var(--color-status-bad)', fg: '#fff', bd: 'transparent', hov: 'color-mix(in srgb, var(--color-status-bad), black 8%)' },
};
function Button({ variant = 'default', size = 'default', disabled, fullWidth, style, children, ...rest }) {
  const [hover, setHover] = useState(false);
  const v = BTN_VARIANT[variant];
  return (
    <button
      disabled={disabled}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ ...BTN_BASE, ...BTN_SIZE[size], backgroundColor: hover && !disabled ? v.hov : v.bg,
        color: v.fg, borderColor: v.bd, opacity: disabled ? 0.5 : 1,
        width: fullWidth ? '100%' : undefined,
        pointerEvents: disabled ? 'none' : 'auto', ...style }}
      {...rest}>
      {children}
    </button>
  );
}

/* ---- Badge / StatusBadge ------------------------------------------------ */
const BADGE = {
  default: { bg: 'var(--color-brand-soft)', fg: 'var(--color-brand)' },
  success: { bg: 'var(--color-status-ok-bg)', fg: 'var(--color-status-ok)' },
  warning: { bg: 'var(--color-status-warn-bg)', fg: 'var(--color-status-warn)' },
  error:   { bg: 'var(--color-status-bad-bg)', fg: 'var(--color-status-bad)' },
  muted:   { bg: 'var(--color-surface-alt)', fg: 'var(--color-text-muted)', bd: 'var(--color-border)' },
};
function Badge({ variant = 'default', children, style }) {
  const b = BADGE[variant];
  return <span style={{ display: 'inline-flex', alignItems: 'center', borderRadius: 'var(--radius-pill)',
    padding: '3px 10px', fontSize: 12, fontWeight: 600, whiteSpace: 'nowrap', background: b.bg, color: b.fg,
    border: b.bd ? `1px solid ${b.bd}` : '1px solid transparent', ...style }}>{children}</span>;
}
const TONE_TO_VARIANT = { success: 'success', warning: 'warning', error: 'error', info: 'default' };
function StatusBadge({ label, tone }) { return <Badge variant={TONE_TO_VARIANT[tone] || 'default'}>{label}</Badge>; }

/* ---- Input / Select / DateInput ----------------------------------------- */
const FIELD = {
  height: 36, width: '100%', borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)',
  background: 'var(--color-surface)', padding: '0 12px', fontSize: 14, fontWeight: 500,
  color: 'var(--color-text)', fontFamily: 'var(--font-ui)', outline: 'none',
};
function Input({ style, ...rest }) {
  const [f, setF] = useState(false);
  return <input onFocus={() => setF(true)} onBlur={() => setF(false)}
    style={{ ...FIELD, boxShadow: f ? '0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-brand)' : 'none', ...style }} {...rest} />;
}
function Select({ style, children, ...rest }) {
  const [f, setF] = useState(false);
  return <select className="ae-select" onFocus={() => setF(true)} onBlur={() => setF(false)}
    style={{ ...FIELD, height: 34, fontSize: 13, boxShadow: f ? '0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-brand)' : 'none', ...style }} {...rest}>{children}</select>;
}
function DateInput({ style, ...rest }) { return <Input type="date" style={{ height: 34, fontSize: 13, ...style }} {...rest} />; }

/* ---- Toggle (switch) ----------------------------------------------------- */
function Toggle({ checked, onChange, disabled, ariaLabel }) {
  return (
    <button type="button" role="switch" aria-checked={!!checked} aria-label={ariaLabel} disabled={disabled}
      onClick={() => !disabled && onChange(!checked)}
      style={{ width: 40, height: 23, borderRadius: 999, border: 'none', padding: 2, flexShrink: 0,
        cursor: disabled ? 'default' : 'pointer', opacity: disabled ? 0.5 : 1,
        background: checked ? 'var(--color-brand)' : 'var(--color-border)', transition: 'background-color .15s' }}>
      <span style={{ display: 'block', width: 19, height: 19, borderRadius: 999, background: '#fff',
        boxShadow: '0 1px 2px rgba(15,23,42,.3)', transform: checked ? 'translateX(17px)' : 'translateX(0)', transition: 'transform .15s ease' }} />
    </button>
  );
}

/* ---- Dropdown — custom select (replaces native <select>) ----------------- */
function Dropdown({ value, options, onChange, width = 160, ariaLabel, placeholder = 'Select…' }) {
  const opts = options.map((o) => (typeof o === 'string' ? { value: o, label: o } : o));
  const [open, setOpen] = useState(false);
  const [hi, setHi] = useState(-1);
  const ref = useRef(null);
  const current = opts.find((o) => o.value === value);
  useEffect(() => {
    if (!open) return;
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);
  const choose = (v) => { onChange(v); setOpen(false); };
  const onKey = (e) => {
    if (e.key === 'Escape') { setOpen(false); return; }
    if (e.key === 'ArrowDown') { e.preventDefault(); if (!open) setOpen(true); else setHi((h) => Math.min(opts.length - 1, h + 1)); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setHi((h) => Math.max(0, h - 1)); }
    if (e.key === 'Enter' && open && hi >= 0) { e.preventDefault(); choose(opts[hi].value); }
  };
  return (
    <div ref={ref} style={{ position: 'relative', width }}>
      <button type="button" aria-haspopup="listbox" aria-expanded={open} aria-label={ariaLabel}
        onClick={() => setOpen((o) => !o)} onKeyDown={onKey}
        style={{ ...FIELD, height: 34, fontSize: 13, display: 'inline-flex', alignItems: 'center',
          justifyContent: 'space-between', gap: 8, cursor: 'pointer', textAlign: 'left',
          boxShadow: open ? '0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-brand)' : 'none' }}>
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: current ? 'var(--color-text)' : 'var(--color-text-muted)' }}>{current ? current.label : placeholder}</span>
        <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="var(--color-text-muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, transition: 'transform .12s', transform: open ? 'rotate(180deg)' : 'none' }}><polyline points="6 8 10 12 14 8" /></svg>
      </button>
      {open ? (
        <ul role="listbox" style={{ position: 'absolute', zIndex: 40, top: 'calc(100% + 4px)', left: 0, width: '100%',
          margin: 0, padding: 4, listStyle: 'none', background: 'var(--color-surface)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-sm)', boxShadow: '0 12px 30px rgba(15,23,42,.16)', maxHeight: 240, overflow: 'auto' }}>
          {opts.map((o, i) => {
            const active = o.value === value, hot = i === hi;
            return (
              <li key={o.value} role="option" aria-selected={active}
                onMouseEnter={() => setHi(i)} onMouseDown={(e) => { e.preventDefault(); choose(o.value); }}
                style={{ padding: '7px 10px', borderRadius: 6, fontSize: 13, cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
                  background: hot ? 'var(--color-surface-alt)' : 'transparent',
                  color: active ? 'var(--color-brand)' : 'var(--color-text)', fontWeight: active ? 600 : 500 }}>
                {o.label}
                {active ? <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg> : null}
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}

/* ---- ControlGroup / FilterBar ------------------------------------------- */
function ControlGroup({ label, children }) {
  return <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><span className="sr-only">{label}</span>{children}</label>;
}
function FilterBar({ actions, children, bordered = true }) {
  return (
    <div style={bordered ? { borderRadius: 'var(--radius-sm)', border: '1px solid var(--color-border)',
      background: 'var(--color-surface)', padding: 12 } : null}>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>{children}</div>
        {actions ? <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>{actions}</div> : null}
      </div>
    </div>
  );
}

/* ---- PageHeader / PageCard ---------------------------------------------- */
function PageHeader({ title, subtitle, children }) {
  return (
    <header style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
      <div>
        <h2 style={{ margin: 0, fontSize: 20, fontWeight: 600 }}>{title}</h2>
        {subtitle ? <p style={{ margin: '4px 0 0', fontSize: 14, color: 'var(--color-text-muted)' }}>{subtitle}</p> : null}
      </div>
      {children ? <div style={{ flexShrink: 0 }}>{children}</div> : null}
    </header>
  );
}
function PageCard({ title, subtitle, children, style }) {
  return (
    <section style={{ borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
      background: 'var(--color-surface)', padding: 20, boxShadow: 'var(--shadow-subtle)', ...style }}>
      {title ? <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>{title}</h2> : null}
      {subtitle ? <p style={{ margin: '4px 0 0', fontSize: 14, color: 'var(--color-text-muted)' }}>{subtitle}</p> : null}
      <div style={{ marginTop: title ? 16 : 0 }}>{children}</div>
    </section>
  );
}

/* ---- DataTable — refined "ledger" treatment ----------------------------- */
/* Cleaner than a boxed grid: tracked uppercase header, hairline row rules,
   a leading brand accent rail that lights on hover/selection. Columns may set
   `accent: (row) => cssColor` to show a per-row status tick in the first cell. */
function DataTable({ columns, rows, getRowKey, onRowClick, selectedRowKey = null, emptyMessage, rowClassName, accent }) {
  if (!rows.length) return <p style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>{emptyMessage || 'No results.'}</p>;
  return (
    <div style={{ overflowX: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', background: 'var(--color-surface)' }}>
      <table style={{ minWidth: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 13.5 }}>
        <thead>
          <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
            {accent ? <th style={{ width: 0, padding: 0 }} aria-hidden="true"></th> : null}
            {columns.map((c) => <th key={c.key} style={{ padding: '11px 16px', fontWeight: 600, fontSize: 11,
              letterSpacing: '.05em', textTransform: 'uppercase', color: 'var(--color-text-muted)', whiteSpace: 'nowrap', ...(c.headStyle || {}) }}>{c.header}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => {
            const k = getRowKey(row);
            const sel = selectedRowKey !== null && selectedRowKey === k;
            return (
              <Row key={k} selected={sel} clickable={!!onRowClick} last={idx === rows.length - 1}
                   onClick={onRowClick ? () => onRowClick(row) : undefined}
                   className={rowClassName ? rowClassName(row) : ''}>
                {accent ? <td style={{ width: 4, padding: 0, background: accent(row) || 'transparent' }} aria-hidden="true"></td> : null}
                {columns.map((c) => <td key={c.key} style={{ padding: '12px 16px', ...(c.cellStyle || {}) }}>{c.render(row)}</td>)}
              </Row>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
function Row({ selected, clickable, onClick, className, last, children }) {
  const [hover, setHover] = useState(false);
  const bg = selected ? 'var(--color-brand-soft)' : (hover && clickable ? 'var(--color-surface-alt)' : 'transparent');
  const rail = selected ? 'inset 3px 0 0 var(--color-brand)' : (hover && clickable ? 'inset 3px 0 0 color-mix(in srgb, var(--color-brand) 45%, transparent)' : 'inset 3px 0 0 transparent');
  return (
    <tr className={className}
      role={clickable ? 'button' : undefined} tabIndex={clickable ? 0 : undefined}
      onClick={onClick}
      onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick && onClick(); } } : undefined}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ borderBottom: last ? 'none' : '1px solid color-mix(in srgb, var(--color-border) 60%, transparent)', background: bg, cursor: clickable ? 'pointer' : 'default', boxShadow: rail }}>
      {children}
    </tr>
  );
}

/* ---- ConfirmDialog ------------------------------------------------------ */
function ConfirmDialog({ open, title, description, confirmLabel = 'Confirm', cancelLabel = 'Cancel', onConfirm, onCancel }) {
  if (!open) return null;
  return (
    <div onClick={onCancel} style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'rgba(15,23,42,.3)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
      <div onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true"
        style={{ width: 'min(420px,100%)', background: 'var(--color-surface)', borderRadius: 'var(--radius-md)',
          border: '1px solid var(--color-border)', boxShadow: '0 20px 50px rgba(15,23,42,.25)', padding: 22 }}>
        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{title}</h3>
        <p style={{ margin: '8px 0 18px', fontSize: 14, color: 'var(--color-text-muted)', lineHeight: 1.5 }}>{description}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button variant="outline" size="sm" onClick={onCancel}>{cancelLabel}</Button>
          <Button size="sm" onClick={onConfirm}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}

/* ---- Toast -------------------------------------------------------------- */
function Toast({ toast }) {
  if (!toast) return null;
  const map = { success: 'success', error: 'error', warning: 'warning', info: 'default' };
  const b = BADGE[map[toast.kind] || 'default'];
  return (
    <div style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 70, background: 'var(--color-surface)',
      border: `1px solid ${b.fg}`, borderLeft: `4px solid ${b.fg}`, borderRadius: 'var(--radius-sm)',
      boxShadow: '0 10px 30px rgba(15,23,42,.18)', padding: '12px 16px', maxWidth: 340, fontSize: 13 }}>
      <span style={{ fontWeight: 600, color: b.fg }}>{toast.title}</span>
      {toast.body ? <span style={{ color: 'var(--color-text)', marginLeft: 6 }}>{toast.body}</span> : null}
    </div>
  );
}

Object.assign(window, {
  Button, Badge, StatusBadge, Input, Select, Dropdown, DateInput, Toggle, ControlGroup,
  FilterBar, PageHeader, PageCard, DataTable, ConfirmDialog, Toast,
});
