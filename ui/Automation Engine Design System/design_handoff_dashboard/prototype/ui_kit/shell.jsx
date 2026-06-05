/* App shell — four-region layout: icon Rail · contextual Panel · Content · Inspector.
   Mirrors ui/src/app/AppShell.tsx + AppRail.tsx (rail is icons-only). */
const { useState: useStateShell } = React;

const ThemeCtx = React.createContext({ theme: 'light', toggle: () => {} });

const NAV = [
  { key: 'dashboard', label: 'Dashboard',
    icon: <g><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></g> },
  { key: 'webhooks', label: 'Webhooks',
    icon: <g><path d="M12 2a10 10 0 0 1 10 10"/><path d="M12 8a6 6 0 0 1 6 6"/><circle cx="12" cy="18" r="2"/></g> },
  { key: 'processed-calls', label: 'Processed Calls',
    icon: <g><path d="M5 4h4l1.5 4-2 1a11 11 0 0 0 5 5l1-2 4 1.5V18a2 2 0 0 1-2 2A14 14 0 0 1 5 6z"/></g> },
  { key: 'workflows', label: 'Workflows',
    icon: <g><path d="M12 3 21 8l-9 5-9-5z"/><path d="m3 13 9 5 9-5"/></g> },
  { key: 'persons', label: 'Persons',
    icon: <g><circle cx="9" cy="8" r="3"/><path d="M3 20a6 6 0 0 1 12 0"/><path d="M16 4a3 3 0 0 1 0 6"/></g> },
  { key: 'settings', label: 'Settings',
    icon: <g><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></g> },
];

function LogoMark({ size = 30 }) {
  return (
    <span style={{ height: size, width: size, borderRadius: 8, background: 'var(--color-brand)',
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <svg viewBox="0 0 24 24" width={size * 0.56} height={size * 0.56} fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="6" cy="12" r="2.3"/><circle cx="17" cy="6.5" r="2.3"/><circle cx="17" cy="17.5" r="2.3"/><path d="M8 11 15 7.2M8 13 15 16.8"/>
      </svg>
    </span>
  );
}

function RailItem({ item, active, onClick }) {
  const [hover, setHover] = useStateShell(false);
  return (
    <button title={item.label} aria-label={item.label} aria-current={active ? 'page' : undefined}
      onClick={onClick} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ height: 40, width: 40, borderRadius: 8, border: 'none', display: 'inline-flex',
        alignItems: 'center', justifyContent: 'center',
        background: active ? 'var(--color-brand)' : (hover ? 'var(--color-surface-alt)' : 'transparent'),
        color: active ? '#fff' : 'var(--color-text-muted)' }}>
      <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{item.icon}</svg>
    </button>
  );
}

function Rail({ active, onNav, onLogout, theme, onToggleTheme }) {
  return (
    <aside aria-label="Global navigation rail"
      style={{ width: 64, flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'center',
        justifyContent: 'space-between', borderRight: '1px solid var(--color-border)',
        background: 'var(--color-surface)', padding: '16px 0' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
        <button onClick={() => onNav('dashboard')} aria-label="Home" style={{ border: 'none', background: 'none', marginBottom: 8, padding: 0 }}><LogoMark /></button>
        {NAV.map((item) => <RailItem key={item.key} item={item} active={active === item.key} onClick={() => onNav(item.key)} />)}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
        <button onClick={onToggleTheme} title={theme === 'dark' ? 'Switch to light' : 'Switch to dark'} aria-label="Toggle theme"
          style={{ height: 40, width: 40, borderRadius: 8, border: 'none', background: 'transparent',
            color: 'var(--color-text-muted)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
          {theme === 'dark' ? <SunIcon size={18} /> : <MoonIcon size={18} />}
        </button>
        <button onClick={onLogout} title="Log out" aria-label="Log out"
          style={{ height: 40, width: 40, borderRadius: 8, border: 'none', background: 'transparent',
            color: 'var(--color-text-muted)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
          <LogoutIcon size={18} />
        </button>
      </div>
    </aside>
  );
}

function Panel({ title, children }) {
  if (!title && !children) return null;
  return (
    <section aria-label="Section panel"
      style={{ width: 248, flexShrink: 0, borderRight: '1px solid var(--color-border)',
        background: 'var(--color-surface)', padding: 16, overflowY: 'auto' }}>
      {title ? <h2 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>{title}</h2> : null}
      {children ? <div style={{ marginTop: title ? 12 : 0, fontSize: 14, color: 'var(--color-text-muted)' }}>{children}</div> : null}
    </section>
  );
}

function Inspector({ title = 'Inspector', children }) {
  return (
    <aside aria-label="Inspector region"
      style={{ width: 320, flexShrink: 0, borderLeft: '1px solid var(--color-border)',
        background: 'var(--color-surface)', padding: 16, overflowY: 'auto' }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>{title}</h3>
      <div style={{ marginTop: 12 }}>{children}</div>
    </aside>
  );
}

function ShellLayout({ active, onNav, onLogout, panel, inspector, children }) {
  const { theme, toggle } = React.useContext(ThemeCtx);
  return (
    <div style={{ display: 'flex', height: '100%', minHeight: 0, background: 'var(--color-bg)', color: 'var(--color-text)' }}>
      <Rail active={active} onNav={onNav} onLogout={onLogout} theme={theme} onToggleTheme={toggle} />
      {panel ? <Panel title={panel.title}>{panel.body}</Panel> : null}
      <main aria-label="Workspace content" style={{ flex: 1, minWidth: 0, minHeight: 0, overflow: 'auto', padding: 24 }}>
        {children}
      </main>
      {inspector ? <Inspector title={inspector.title}>{inspector.body}</Inspector> : null}
    </div>
  );
}

Object.assign(window, { NAV, LogoMark, Rail, Panel, Inspector, ShellLayout, ThemeCtx });
