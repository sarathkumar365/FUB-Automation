/* Full-page status family for Automation Engine.
   ----------------------------------------------------------------------------
   A shared FullPageStatus shell + StatusContent renderer drives all three
   undesigned system pages so they read as one family:

     AppErrorFallback   — crash / error-boundary fallback   (tone: bad)
     NotFoundPage       — 404, standalone OR inside the shell (tone: brand)
     SessionDisabledPage— admin UI access gated server-side  (tone: warn)

   Bare-on-gradient (no card), one iconographic status glyph per page, echoing
   the AuthShell lockup + ambient brand radial. Three shell treatments (A/B/C)
   are exposed via a `variant` prop so the family direction can be chosen.
   Token-driven only — light + dark both correct. */

const { useState: useStatusState } = React;

/* Entrance reveal — a TRANSFORM-only rise (never touches opacity). A
   throttled/background iframe freezes animations at frame 0; with no opacity
   keyframe the worst case is content resting a few px low — always visible,
   never stranded hidden. In a foreground tab it plays as a soft rise. */
function useRise(delay = 0) {
  const ref = React.useRef(null);
  React.useEffect(() => {
    const el = ref.current;
    if (!el || !el.animate) return;
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    try {
      el.animate(
        [{ transform: 'translateY(14px)' }, { transform: 'translateY(0)' }],
        { duration: 540, delay, easing: 'cubic-bezier(.2,.7,.3,1)' }
      );
    } catch (e) { /* visible base is fine */ }
  }, [delay]);
  return ref;
}

/* ── ambient brand gradient — the family signature (from AuthShell) ──────── */
const AE_GRADIENT =
  'radial-gradient(760px 360px at 50% -14%, color-mix(in srgb, var(--color-brand) 13%, transparent), transparent 64%), ' +
  'radial-gradient(560px 300px at 86% 114%, color-mix(in srgb, var(--color-brand-2) 10%, transparent), transparent 60%), ' +
  'var(--color-bg)';

const TONES = {
  bad:   { fg: 'var(--color-status-bad)',  bg: 'var(--color-status-bad-bg)'  },
  warn:  { fg: 'var(--color-status-warn)', bg: 'var(--color-status-warn-bg)' },
  brand: { fg: 'var(--color-brand)',       bg: 'var(--color-brand-soft)'     },
};

/* ── status glyphs — Lucide-style, stroke 1.8, currentColor ─────────────── */
function GlyphSvg({ size = 28, sw = 1.8, children, style }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor"
      strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={style}>
      {children}
    </svg>
  );
}
const AlertGlyph = (p) => (
  <GlyphSvg {...p}><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><path d="M12 9v4" /><path d="M12 17h.01" /></GlyphSvg>
);
const CompassGlyph = (p) => (
  <GlyphSvg {...p}><circle cx="12" cy="12" r="9.2" /><path d="m15.6 8.4-2.1 5.1-5.1 2.1 2.1-5.1z" /></GlyphSvg>
);
const LockGlyph = (p) => (
  <GlyphSvg {...p}><rect x="4.5" y="10.8" width="15" height="9.7" rx="2.2" /><path d="M8 10.8V7.4a4 4 0 0 1 8 0v3.4" /></GlyphSvg>
);
const ChevronLeft = (p) => <GlyphSvg {...p} sw={2}><path d="m14.5 17-5-5 5-5" /></GlyphSvg>;
const ChevronDown = ({ open, ...p }) => (
  <GlyphSvg {...p} sw={2} style={{ transition: 'transform .15s ease', transform: open ? 'rotate(0deg)' : 'rotate(-90deg)' }}><path d="m6 9 6 6 6-6" /></GlyphSvg>
);

const SAMPLE_ERR =
  "TypeError: Cannot read properties of undefined (reading 'status')\n" +
  '    at RunRow (screens-processed-workflows.tsx:142:18)\n' +
  '    at renderWithHooks (react-dom.development.js:15486:18)\n' +
  '    at mountIndeterminateComponent (react-dom.development.js:20103:13)\n' +
  '    at beginWork (react-dom.development.js:21626:16)\n' +
  '    at HTMLUnknownElement.callCallback (react-dom.development.js:4164:14)';

/* ── shared bits ────────────────────────────────────────────────────────── */
function Lockup({ align = 'center' }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 11, justifyContent: align === 'center' ? 'center' : 'flex-start' }}>
      <LogoMark size={34} />
      <div style={{ textAlign: 'left' }}>
        <div style={{ fontSize: 15, fontWeight: 800, letterSpacing: '-.01em', color: 'var(--color-text)' }}>Automation Engine</div>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--color-text-muted)' }}>Operations console</div>
      </div>
    </div>
  );
}

function Eyebrow({ tone, children }) {
  const t = TONES[tone];
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, color: t.fg, fontSize: 11, fontWeight: 800, letterSpacing: '.14em', textTransform: 'uppercase' }}>
      <span style={{ width: 6, height: 6, borderRadius: 999, background: t.fg, flexShrink: 0 }} />
      {children}
    </span>
  );
}
function PillEyebrow({ tone, Glyph, children }) {
  const t = TONES[tone];
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: t.bg, color: t.fg,
      padding: '6px 13px 6px 10px', borderRadius: 999, fontSize: 11, fontWeight: 800, letterSpacing: '.13em', textTransform: 'uppercase' }}>
      {Glyph ? <Glyph size={14} sw={2} /> : null}{children}
    </span>
  );
}

function QuietLink({ children, href = '#', icon }) {
  return (
    <a href={href} className="aestatus-link" style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-muted)',
      textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      {icon}{children}
    </a>
  );
}

function Actions({ primary, secondary, justify = 'center' }) {
  if (!primary && !secondary) return null;
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: justify, gap: 16, marginTop: 26, flexWrap: 'wrap' }}>
      {primary ? <Button size="lg" onClick={primary.onClick}>{primary.label}</Button> : null}
      {secondary ? <QuietLink href={secondary.href} icon={secondary.icon}>{secondary.label}</QuietLink> : null}
    </div>
  );
}

/* Dev-only collapsible error stack — mono in a muted surface. */
function ErrorDetails({ open = false }) {
  const [o, setO] = useStatusState(open);
  return (
    <div style={{ marginTop: 26, width: '100%', maxWidth: 440, textAlign: 'left' }}>
      <button type="button" onClick={() => setO((v) => !v)} className="aestatus-details-btn"
        style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: 'none', border: 'none', padding: 0,
          color: 'var(--color-text-muted)', fontFamily: 'var(--font-ui)', cursor: 'pointer' }}>
        <ChevronDown open={o} size={15} />
        <span style={{ fontSize: 11, fontWeight: 800, letterSpacing: '.1em', textTransform: 'uppercase' }}>Error details</span>
        <span className="mono" style={{ fontSize: 10.5, fontWeight: 500, letterSpacing: 0, padding: '2px 6px', borderRadius: 6,
          background: 'var(--color-surface-alt)', border: '1px solid var(--color-border)' }}>dev only</span>
      </button>
      {o ? (
        <pre className="mono" style={{ margin: '12px 0 0', padding: '13px 15px', background: 'var(--color-surface-alt)',
          border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', fontSize: 11.5, lineHeight: 1.65,
          color: 'var(--color-text-muted)', overflow: 'auto', maxHeight: 168, whiteSpace: 'pre-wrap' }}>{SAMPLE_ERR}</pre>
      ) : null}
    </div>
  );
}

/* ── shared title + body ────────────────────────────────────────────────── */
function StatusTitle({ children, size = 30 }) {
  return <h1 style={{ margin: 0, fontSize: size, fontWeight: 800, letterSpacing: '-.025em', lineHeight: 1.1, color: 'var(--color-text)' }}>{children}</h1>;
}
function StatusBody({ children, maxWidth = '42ch' }) {
  return <p style={{ margin: '14px 0 0', fontSize: 15, lineHeight: 1.55, color: 'var(--color-text-muted)', maxWidth, textWrap: 'pretty' }}>{children}</p>;
}
function Helper({ children }) {
  return <div style={{ marginTop: 14, fontSize: 13.5, lineHeight: 1.5, color: 'var(--color-text-muted)' }}>{children}</div>;
}
/* Mono console status strip — shared by Direction C and the B+C final. */
function StatusStrip({ tone, children }) {
  const t = TONES[tone];
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, padding: '9px 14px',
      border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)',
      background: 'var(--color-surface)', boxShadow: 'var(--shadow-subtle)' }}>
      <span style={{ width: 7, height: 7, borderRadius: 999, background: t.fg, flexShrink: 0 }} />
      <span className="mono" style={{ fontSize: 12, color: 'var(--color-text)', whiteSpace: 'nowrap' }}>{children}</span>
    </div>
  );
}

/* ── Direction A — Quiet center ─────────────────────────────────────────── */
function DirA(p) {
  const t = TONES[p.tone];
  const rise = useRise();
  return (
    <div ref={rise} style={{ maxWidth: 470, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
      <div style={{ width: 76, height: 76, borderRadius: '50%', background: t.bg, color: t.fg,
        display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 22 }}>
        <p.Glyph size={32} />
      </div>
      <div style={{ marginBottom: 14 }}><Eyebrow tone={p.tone}>{p.eyebrow}</Eyebrow></div>
      <StatusTitle>{p.title}</StatusTitle>
      <StatusBody>{p.body}</StatusBody>
      {p.helper ? <Helper>{p.helper}</Helper> : null}
      <Actions primary={p.primary} secondary={p.secondary} justify="center" />
      {p.extra}
    </div>
  );
}

/* ── Direction B — Ghost glyph watermark ────────────────────────────────── */
function DirB(p) {
  const t = TONES[p.tone];
  const rise = useRise();
  return (
    <div style={{ position: 'relative', maxWidth: 560, width: '100%', display: 'flex', justifyContent: 'center' }}>
      <div aria-hidden="true" style={{ position: 'absolute', top: '50%', left: '50%',
        transform: 'translate(-50%, -56%)', color: t.fg, opacity: 0.07, pointerEvents: 'none', lineHeight: 1 }}>
        {p.watermark ? p.watermark : <p.Glyph size={372} sw={1.1} />}
      </div>
      <div ref={rise} style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{ marginBottom: 20 }}><PillEyebrow tone={p.tone} Glyph={p.Glyph}>{p.eyebrow}</PillEyebrow></div>
        <StatusTitle size={32}>{p.title}</StatusTitle>
        <StatusBody>{p.body}</StatusBody>
        {p.helper ? <Helper>{p.helper}</Helper> : null}
        {p.strip && p.meta ? <div style={{ marginTop: 20 }}><StatusStrip tone={p.tone}>{p.meta}</StatusStrip></div> : null}
        <Actions primary={p.primary} secondary={p.secondary} justify="center" />
        {p.extra}
      </div>
    </div>
  );
}

/* ── Direction C — Console status line ──────────────────────────────────── */
function DirC(p) {
  const t = TONES[p.tone];
  const INDENT = 84;
  const rise = useRise();
  return (
    <div ref={rise} style={{ maxWidth: 580, width: '100%' }}>
      <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
        <div style={{ width: 64, height: 64, borderRadius: 'var(--radius-md)', background: t.bg, color: t.fg,
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <p.Glyph size={28} />
        </div>
        <div style={{ flex: 1, minWidth: 0, paddingTop: 2 }}>
          <div style={{ marginBottom: 12 }}><Eyebrow tone={p.tone}>{p.eyebrow}</Eyebrow></div>
          <StatusTitle size={27}>{p.title}</StatusTitle>
          <StatusBody maxWidth="46ch">{p.body}</StatusBody>
          {p.helper ? <Helper>{p.helper}</Helper> : null}
        </div>
      </div>
      <div style={{ marginTop: 22, marginLeft: INDENT }}>
        <StatusStrip tone={p.tone}>{p.meta}</StatusStrip>
      </div>
      <div style={{ marginLeft: INDENT }}>
        <Actions primary={p.primary} secondary={p.secondary} justify="flex-start" />
        {p.extra}
      </div>
    </div>
  );
}

function Direction({ variant, ...p }) {
  if (variant === 'B') return <DirB {...p} />;
  if (variant === 'C') return <DirC {...p} />;
  return <DirA {...p} />;
}

/* ── FullPageStatus — the shared shell (gradient + lockup + centered slot) ── */
function FullPageStatus({ inShell = false, hideLockup = false, children }) {
  return (
    <div className="aestatus-root" style={{ position: 'relative', height: '100%', width: '100%', overflow: 'hidden',
      background: inShell ? 'transparent' : AE_GRADIENT, color: 'var(--color-text)', fontFamily: 'var(--font-ui)',
      display: 'flex', flexDirection: 'column' }}>
      {!inShell && !hideLockup ? (
        <div style={{ position: 'absolute', top: 40, left: 0, right: 0, display: 'flex', justifyContent: 'center' }}>
          <Lockup />
        </div>
      ) : null}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: inShell ? 32 : '104px 40px 56px' }}>
        {children}
      </div>
    </div>
  );
}

/* ── Page content definitions ───────────────────────────────────────────── */
function errorContent(detailsOpen) {
  return {
    tone: 'bad', Glyph: AlertGlyph,
    eyebrow: 'Error',
    title: 'Something went wrong',
    body: 'The console hit an unexpected error. Reloading usually fixes it.',
    primary: { label: 'Reload', onClick: () => {} },
    secondary: { label: 'Go to dashboard', href: '#' },
    meta: 'BOUNDARY · render error caught · 500',
    extra: <ErrorDetails open={detailsOpen} />,
  };
}
function notFoundContent() {
  return {
    tone: 'brand', Glyph: CompassGlyph,
    eyebrow: '404 · Not found',
    title: 'Page not found',
    body: 'The page you are looking for does not exist or has moved.',
    primary: { label: 'Go to dashboard', onClick: () => {} },
    secondary: { label: 'Back', href: '#', icon: <ChevronLeft size={16} /> },
    meta: 'GET /admin-ui/leads/9f2a · 404',
    watermark: <div style={{ fontFamily: 'var(--font-ui)', fontWeight: 800, fontSize: 300, letterSpacing: '-.04em' }}>404</div>,
  };
}
function sessionContent() {
  return {
    tone: 'warn', Glyph: LockGlyph,
    eyebrow: 'Restricted',
    title: 'Admin access is disabled',
    body: 'Session guard is enabled and admin UI access is currently turned off. Enable admin UI access to restore the workspace.',
    helper: <span>No action needed here — ask a <span style={{ color: 'var(--color-text)', fontWeight: 600 }}>workspace administrator</span> to re-enable admin UI access.</span>,
    secondary: { label: 'Return to sign in', href: '#' },
    meta: 'GUARD · admin-ui access disabled',
  };
}

/* ── Page wrappers — the deliverables ───────────────────────────────────── */
function AppErrorFallback({ variant = 'A', detailsOpen = false, strip = false }) {
  return <FullPageStatus><Direction variant={variant} strip={strip} {...errorContent(detailsOpen)} /></FullPageStatus>;
}

function NotFoundPage({ variant = 'A', inShell = false, strip = false }) {
  const content = notFoundContent();
  if (inShell) {
    return (
      <ShellLayout active="dashboard" onNav={() => {}} onLogout={() => {}}>
        <FullPageStatus inShell hideLockup><DirA {...content} /></FullPageStatus>
      </ShellLayout>
    );
  }
  return <FullPageStatus><Direction variant={variant} strip={strip} {...content} /></FullPageStatus>;
}

function SessionDisabledPage({ variant = 'A', strip = false }) {
  return <FullPageStatus><Direction variant={variant} strip={strip} {...sessionContent()} /></FullPageStatus>;
}

Object.assign(window, {
  FullPageStatus, Direction, DirA, DirB, DirC,
  AppErrorFallback, NotFoundPage, SessionDisabledPage,
});
