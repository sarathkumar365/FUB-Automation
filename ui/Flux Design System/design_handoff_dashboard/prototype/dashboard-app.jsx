/* Direction A — Health headline, built out as a real, interactive dashboard.
   Working light/dark toggle (rail), live throughput tick, conversion rates on
   the funnel, replay → confirm → toast, clickable runs, and a Tweaks panel. */
const { useState: useAS, useEffect: useAE, useRef: useAR } = React;

/* ---- theme provider so the rail's sun/moon toggle actually works -------- */
function ThemeProvider({ children }) {
  const [theme, setTheme] = useAS('light');
  const toggle = () => setTheme((t) => (t === 'light' ? 'dark' : 'light'));
  return (
    <ThemeCtx.Provider value={{ theme, toggle }}>
      <div data-theme={theme === 'dark' ? 'dark' : undefined} style={{ height: '100%' }}>{children}</div>
    </ThemeCtx.Provider>
  );
}

/* ---- hero stat with optional delta -------------------------------------- */
function HeroStat({ label, value, tone, delta, deltaTone = 'ok' }) {
  const up = delta && String(delta).trim().startsWith('+');
  const dColor = deltaTone === 'bad' ? 'var(--color-status-bad)' : deltaTone === 'muted' ? 'var(--color-text-muted)' : 'var(--color-status-ok)';
  return (
    <div style={{ padding: '0 22px' }}>
      <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.05em', textTransform: 'uppercase', color: 'var(--color-text-muted)' }}>{label}</div>
      <div className="ae-bignum mono" style={{ fontSize: 26, fontWeight: 700, marginTop: 6, color: tone === 'bad' ? 'var(--color-status-bad)' : 'var(--color-text)' }}>{value}</div>
      {delta ? (
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 3, marginTop: 5, fontSize: 11, fontWeight: 700, color: dColor }}>
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" style={{ transform: up ? 'none' : 'rotate(180deg)' }}><path d="M12 19V5M5 12l7-7 7 7" /></svg>
          {delta}
        </div>
      ) : <div style={{ height: 22 }} />}
    </div>
  );
}

/* ---- funnel rail with conversion rates ---------------------------------- */
function FunnelStage({ stage, openCount }) {
  const bad = stage.tone === 'bad';
  const color = bad ? 'var(--color-status-bad)' : 'var(--color-brand)';
  const value = stage.key === 'failed' ? openCount : stage.value;
  return (
    <div style={{ flex: 1, minWidth: 0, padding: '16px 22px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
        <span style={{ width: 7, height: 7, borderRadius: 999, background: color }} />
        <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.05em', textTransform: 'uppercase', color: 'var(--color-text-muted)' }}>{stage.label}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 9 }}>
        <span className="ae-bignum mono" style={{ fontSize: 30, fontWeight: 700, color: bad ? 'var(--color-status-bad)' : 'var(--color-text)' }}>{value}</span>
        <span style={{ fontSize: 11, color: 'var(--color-text-muted)' }}>{stage.sub}</span>
      </div>
      <div style={{ marginTop: 11 }}><Sparkbars data={stage.spark} height={26} color={color} /></div>
    </div>
  );
}
function Connector({ conv, show }) {
  const color = conv.tone === 'bad' ? 'var(--color-status-bad)' : conv.tone === 'neutral' ? 'var(--color-text-muted)' : 'var(--color-status-ok)';
  return (
    <div style={{ flexShrink: 0, alignSelf: 'stretch', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
      borderLeft: '1px solid var(--color-border)', padding: '0 14px', minWidth: show ? 64 : 18 }}>
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="color-mix(in srgb, var(--color-text-muted) 65%, transparent)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
      {show ? (
        <div style={{ textAlign: 'center' }}>
          <div className="mono ae-tnum" style={{ fontSize: 12.5, fontWeight: 700, color }}>{conv.pct}</div>
          <div style={{ fontSize: 9.5, fontWeight: 600, letterSpacing: '.03em', textTransform: 'uppercase', color: 'var(--color-text-muted)', marginTop: 1 }}>{conv.cap}</div>
        </div>
      ) : null}
    </div>
  );
}
function FunnelRail({ openCount, conversions }) {
  const convs = [
    { pct: Math.round(STAGES[1].value / STAGES[0].value * 100) + '%', cap: 'normalized', tone: 'ok' },
    { pct: Math.round(STAGES[2].value / STAGES[1].value * 100) + '%', cap: 'to runs', tone: 'neutral' },
    { pct: (openCount / STAGES[2].value * 100).toFixed(1) + '%', cap: 'fail rate', tone: 'bad' },
  ];
  return (
    <section style={{ display: 'flex', alignItems: 'stretch', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
      background: 'var(--color-surface)', boxShadow: 'var(--shadow-subtle)', overflow: 'hidden' }}>
      {STAGES.map((s, i) => (
        <React.Fragment key={s.key}>
          <FunnelStage stage={s} openCount={openCount} />
          {i < STAGES.length - 1 ? <Connector conv={convs[i]} show={conversions} /> : null}
        </React.Fragment>
      ))}
    </section>
  );
}

/* ---- live attention row with working replay ----------------------------- */
function AttentionLive({ item, onReplay }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px', borderRadius: 'var(--radius-sm)', background: 'var(--color-status-bad-bg)' }}>
      <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--color-status-bad)',
        background: 'color-mix(in srgb, var(--color-status-bad) 12%, transparent)', borderRadius: 5, padding: '3px 6px', flexShrink: 0 }}>{item.kind}</span>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
          <span className="mono" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--color-status-bad)' }}>{item.ref}</span>
          <span className="mono" style={{ fontSize: 11, color: 'var(--color-text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.reason}</span>
        </div>
        <div className="mono" style={{ fontSize: 10.5, color: 'var(--color-text-muted)', marginTop: 1 }}>{item.wf} · {item.retries} retr{item.retries === 1 ? 'y' : 'ies'} · {item.ago} ago</div>
      </div>
      <Button size="sm" variant="outline" onClick={() => onReplay(item)}>Replay</Button>
    </div>
  );
}

/* ---- run duration ------------------------------------------------------- */
function runDuration(r) {
  if (!r.completedAt) return '—';
  const ms = r.completedAt - r.startedAt;
  return (ms / 1000).toFixed(1) + 's';
}

/* ======================================================================== */
/* App                                                                      */
/* ======================================================================== */
const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "#0f9fb8",
  "conversions": true,
  "range": "24h",
  "live": true
}/*EDITMODE-END*/;

function DashboardApp() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [attention, setAttention] = useAS(ATTENTION);
  const [confirm, setConfirm] = useAS(null);
  const [toast, setToast] = useAS(null);
  const [liveMin, setLiveMin] = useAS(8);
  const toastTimer = useAR(null);

  // live events/min ticker
  useAE(() => {
    if (!t.live) return;
    const id = setInterval(() => setLiveMin(() => 6 + Math.floor(Math.random() * 5)), 2600);
    return () => clearInterval(id);
  }, [t.live]);

  const fireToast = (toastObj) => {
    setToast(toastObj);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 2800);
  };
  const doReplay = () => {
    const item = confirm;
    setConfirm(null);
    setAttention((list) => list.filter((a) => a.ref !== item.ref));
    fireToast({ kind: 'success', title: 'Replay queued', body: `${item.kind} ${item.ref}` });
  };

  const recent = RUNS.slice(0, 5);
  const series = t.range === '12h' ? THRU_24.slice(-12) : THRU_24;
  const brandSoft = `color-mix(in srgb, ${t.accent} 14%, var(--color-surface))`;
  const openCount = attention.length;

  return (
    <ThemeProvider>
      {/* accent override scopes to the whole app */}
      <div style={{ height: '100%', '--color-brand': t.accent, '--color-brand-soft': brandSoft }}>
        <ShellLayout active="dashboard" onNav={() => {}} onLogout={() => {}}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 1240, margin: '0 auto' }}>

            {/* hero */}
            <section style={{ position: 'relative', overflow: 'hidden', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
              background: 'var(--color-surface)', boxShadow: 'var(--shadow-subtle)', padding: 28 }}>
              <div className="ae-herowash" />
              <div style={{ position: 'relative', zIndex: 1, display: 'grid', gridTemplateColumns: '1.05fr 1fr', gap: 36, alignItems: 'center' }}>
                <div>
                  <Kicker>Pipeline status · last 24h</Kicker>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 12 }}>
                    <h1 className="ae-bignum" style={{ margin: 0, fontSize: 46, fontWeight: 800, letterSpacing: '-.03em' }}>{openCount === 0 ? 'All clear' : 'Healthy'}</h1>
                    <LivePill />
                  </div>
                  <p style={{ margin: '12px 0 20px', fontSize: 14, color: 'var(--color-text-muted)', lineHeight: 1.5, maxWidth: 440 }}>
                    Event ingestion, normalization and workflow runs are all flowing.{' '}
                    {openCount > 0 ? `${openCount} failed run${openCount === 1 ? '' : 's'} ${openCount === 1 ? 'is' : 'are'} waiting on replay.` : 'No failures need attention.'}
                  </p>
                  <div style={{ display: 'flex', alignItems: 'flex-start', borderTop: '1px solid var(--color-border)', paddingTop: 16 }}>
                    <HeroStat label="Runs" value="312" delta="+8%" deltaTone="ok" />
                    <div style={{ width: 1, alignSelf: 'stretch', background: 'var(--color-border)' }} />
                    <HeroStat label="Success" value="97.0%" delta="+0.3pt" deltaTone="ok" />
                    <div style={{ width: 1, alignSelf: 'stretch', background: 'var(--color-border)' }} />
                    <HeroStat label="Open failures" value={openCount} tone={openCount ? 'bad' : undefined} delta={openCount ? '−2 today' : 'cleared'} deltaTone="ok" />
                  </div>
                </div>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.05em', textTransform: 'uppercase', color: 'var(--color-text-muted)' }}>Throughput · events / hr</span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11, fontWeight: 600, color: 'var(--color-text-muted)' }}>
                      <span className="ae-livedot" style={{ width: 6, height: 6, borderRadius: 999, background: 'var(--color-live)' }} />{liveMin}/min
                    </span>
                  </div>
                  <AreaChart data={series} height={150} color="var(--color-brand)" />
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6 }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>{t.range} ago</span>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>peak 166 · avg 132</span>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--color-text-muted)' }}>now</span>
                  </div>
                </div>
              </div>
            </section>

            {/* funnel rail */}
            <FunnelRail openCount={openCount} conversions={t.conversions} />

            {/* runs + triage */}
            <div style={{ display: 'grid', gridTemplateColumns: '1.7fr 1fr', gap: 16, alignItems: 'start' }}>
              <PageCard title="Recent Runs" subtitle="Latest workflow executions" style={{ padding: 18 }}>
                <DataTable
                  columns={[
                    { key: 'id', header: 'Run', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.id}</span> },
                    { key: 'wf', header: 'Workflow', render: (r) => <span className="mono" style={{ fontSize: 12 }}>{r.workflowKey}</span> },
                    { key: 'st', header: 'Status', render: (r) => <StatusBadge label={r.status} tone={RUN_STATUS_TONE[r.status]} /> },
                    { key: 'dur', header: 'Duration', render: (r) => <span className="mono" style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{runDuration(r)}</span> },
                    { key: 'done', header: 'Completed', render: (r) => <span className="mono" style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{r.completedAt ? fmtTime(r.completedAt) : '—'}</span> },
                  ]}
                  rows={recent} getRowKey={(r) => r.id}
                  onRowClick={(r) => fireToast({ kind: 'info', title: 'Opening run', body: String(r.id) })}
                  accent={(r) => RUN_STATUS_TONE[r.status] === 'error' ? 'var(--color-status-bad)' : RUN_STATUS_TONE[r.status] === 'success' ? 'var(--color-status-ok)' : 'var(--color-brand)'} />
                <div style={{ marginTop: 14 }}><Button variant="outline" size="sm" onClick={() => fireToast({ kind: 'info', title: 'Workflow runs', body: 'opening browse view' })}>Browse all runs</Button></div>
              </PageCard>

              <PageCard title="Needs attention" subtitle={openCount ? `${openCount} failed run${openCount === 1 ? '' : 's'} awaiting replay` : 'Pipeline is healthy'} style={{ padding: 18 }}>
                {openCount === 0 ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '14px 4px', color: 'var(--color-status-ok)', fontSize: 13, fontWeight: 600 }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg>
                    No failed runs — pipeline is healthy.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
                    {attention.map((a) => <AttentionLive key={a.ref} item={a} onReplay={(it) => setConfirm(it)} />)}
                  </div>
                )}
              </PageCard>
            </div>
          </div>

          {/* tweaks */}
          <TweaksPanel>
            <TweakSection label="Appearance" />
            <TweakColor label="Accent" value={t.accent} options={['#0f9fb8', '#0d9488', '#6366f1', '#db2777']} onChange={(v) => setTweak('accent', v)} />
            <TweakSection label="Dashboard" />
            <TweakToggle label="Conversion rates" value={t.conversions} onChange={(v) => setTweak('conversions', v)} />
            <TweakRadio label="Throughput range" value={t.range} options={['12h', '24h']} onChange={(v) => setTweak('range', v)} />
            <TweakToggle label="Live ticker" value={t.live} onChange={(v) => setTweak('live', v)} />
          </TweaksPanel>
        </ShellLayout>

        <ConfirmDialog open={!!confirm} title="Replay failed run?"
          description={confirm ? `This will replay ${confirm.kind} ${confirm.ref} (${confirm.reason}) and re-run automation. Replay is available only for FAILED items.` : ''}
          confirmLabel="Replay" cancelLabel="Cancel" onConfirm={doReplay} onCancel={() => setConfirm(null)} />
        <Toast toast={toast} />
      </div>
    </ThemeProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<DashboardApp />);
