/* Landing — faithful recreation of the product's marketing landing
   (ui/src/modules/landing). Swirling 5-milestone pipeline timeline.
   Adds a slim top bar so the prototype can reach Sign in / Create account. */

const MILESTONES = [
  { cls: 'landing-m1', n: '01', title: 'Event Ingest', body: 'External events arrive — signed Follow Up Boss webhooks today — and are persisted with full payload integrity.' },
  { cls: 'landing-m2', n: '02', title: 'Domain Events', body: 'Raw payloads are normalized into typed domain events the platform can reason about.' },
  { cls: 'landing-m3', n: '03', title: 'Workflow Triggers', body: 'Domain events trigger workflows, matched by event type and rules.' },
  { cls: 'landing-m4', n: '04', title: 'Typed Steps', body: 'Workflows run typed steps — wait, branch, side-effect, compute — to completion.' },
  { cls: 'landing-m5', n: '05', title: 'Audit & Replay', body: 'Every step is recorded; failed runs replay safely with a complete audit trail.' },
];

function LandingPage({ onSignIn, onSignUp }) {
  return (
    <div style={{ height: '100%', overflow: 'auto', background:
      'radial-gradient(720px 320px at 8% -5%, color-mix(in srgb, var(--color-brand) 12%, transparent), transparent 66%), radial-gradient(680px 280px at 92% -2%, color-mix(in srgb, var(--color-brand-2) 9%, transparent), transparent 62%), var(--color-bg)' }}>
      <style>{LANDING_CSS}</style>
      <header className="landing-bar">
        <div className="landing-brand"><LogoMark size={30} /><span>Automation Engine</span></div>
        <div className="landing-bar-actions">
          <button className="landing-link" onClick={onSignIn}>Sign in</button>
          <Button size="sm" onClick={onSignUp}>Create account</Button>
        </div>
      </header>

      <main className="landing-wrap">
        <p className="landing-kicker">AUTOMATION ENGINE</p>
        <h1 className="landing-title">Turn external events into automated, auditable workflows.</h1>
        <p className="landing-subtitle">Ingest events from any source, normalize them into domain events, and run workflows that act on each one — with every step recorded.</p>

        <section className="landing-timeline" aria-label="Story timeline milestones">
          <svg className="landing-path" viewBox="0 0 1240 420" preserveAspectRatio="none" role="img" aria-label="Horizontal swirling milestone path">
            <defs><linearGradient id="landing-curve-gradient" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#67e8f9" /><stop offset="100%" stopColor="#2dd4bf" />
            </linearGradient></defs>
            <path d="M40,110 C170,30 260,205 350,205 C430,205 475,70 580,70 C670,70 715,220 815,220 C910,220 945,90 1060,90 C1135,90 1175,165 1220,165"
              fill="none" stroke="url(#landing-curve-gradient)" strokeWidth="4" strokeLinecap="round" strokeDasharray="3 10" />
          </svg>
          {MILESTONES.map((m) => (
            <article key={m.n} className={`landing-milestone ${m.cls}`}>
              <span className="landing-milestone-tag">Milestone {m.n}</span>
              <h2 className="landing-milestone-title">{m.title}</h2>
              <p className="landing-milestone-body">{m.body}</p>
            </article>
          ))}
        </section>

        <p className="landing-footer-note">Live today: Follow Up Boss event ingestion → workflow automation. More sources and step types coming next.</p>
        <div className="landing-cta">
          <Button size="sm" onClick={onSignIn}>Open Dashboard</Button>
          <Button variant="outline" size="sm" onClick={onSignIn}>View Webhooks</Button>
        </div>
      </main>
    </div>
  );
}

const LANDING_CSS = `
.landing-bar { display:flex; align-items:center; justify-content:space-between; padding:16px 30px; max-width:1240px; margin:0 auto; }
.landing-brand { display:flex; align-items:center; gap:10px; font-size:15px; font-weight:700; letter-spacing:-.01em; }
.landing-bar-actions { display:flex; align-items:center; gap:14px; }
.landing-link { border:none; background:none; font:600 14px var(--font-ui); color:var(--color-text); cursor:pointer; }
.landing-link:hover { color:var(--color-brand); }
.landing-wrap { max-width:1240px; margin:0 auto; padding:18px 30px 40px; display:grid; grid-template-rows:auto auto auto 1fr auto auto; }
.landing-kicker { font-size:11px; letter-spacing:.12em; text-transform:uppercase; color:#0b7285; font-weight:800; margin:8px 0 0; }
.landing-title { margin:8px 0 0; font-size:clamp(30px,4.6vw,56px); font-weight:800; line-height:1.03; letter-spacing:-.03em; max-width:890px; }
.landing-subtitle { margin-top:10px; color:var(--color-text-muted); max-width:720px; font-size:16px; font-weight:500; line-height:1.5; }
.landing-timeline { position:relative; margin-top:20px; height:min(420px,46vh); min-height:360px; }
.landing-path { position:absolute; inset:0; width:100%; height:100%; pointer-events:none; }
.landing-milestone { position:absolute; width:clamp(190px,18vw,240px); background:color-mix(in srgb, var(--color-surface) 80%, transparent); border:1px solid var(--color-border); border-radius:14px; padding:12px 13px 11px; box-shadow:var(--shadow-float); backdrop-filter:blur(4px); }
.landing-milestone-tag { display:inline-block; margin-bottom:7px; font-size:10px; text-transform:uppercase; letter-spacing:.08em; font-weight:700; color:#0f766e; }
.landing-milestone-title { margin:0; font-size:14px; font-weight:700; letter-spacing:-.01em; }
.landing-milestone-body { margin:5px 0 0; font-size:12px; font-weight:500; line-height:1.38; color:var(--color-text-muted); }
.landing-m1 { left:2%; top:18%; } .landing-m2 { left:27%; top:46%; } .landing-m3 { left:53%; top:16%; }
.landing-m4 { left:77%; top:52%; } .landing-m5 { left:82%; top:18%; }
.landing-footer-note { margin-top:8px; color:#0f766e; font-weight:650; font-size:14px; }
.landing-cta { margin-top:12px; display:flex; gap:10px; }
@media (max-width:980px){ .landing-timeline{ height:560px; } .landing-m1{left:2%;top:8%} .landing-m2{left:8%;top:40%} .landing-m3{left:46%;top:8%} .landing-m4{left:52%;top:58%} .landing-m5{left:72%;top:30%} }
:root[data-theme="dark"] .landing-kicker, :root[data-theme="dark"] .landing-footer-note { color:#5eead4; }
:root[data-theme="dark"] .landing-milestone-tag { color:#5eead4; }
`;

Object.assign(window, { LandingPage });
