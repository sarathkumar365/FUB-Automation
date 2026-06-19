/* Root app — public flow (landing → login/signup) then the authed console. */
const { useState: useA, useEffect: useAE } = React;

function App() {
  const [authed, setAuthed] = useA(false);
  const [pub, setPub] = useA('landing');          // landing | login | signup
  const [route, setRoute] = useA('dashboard');
  const [theme, setTheme] = useA(() => localStorage.getItem('ae-theme') || 'light');

  useAE(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('ae-theme', theme);
  }, [theme]);
  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'));

  let body;
  if (!authed) {
    if (pub === 'login') body = <LoginPage onLogin={() => setAuthed(true)} onSignUp={() => setPub('signup')} onBack={() => setPub('landing')} />;
    else if (pub === 'signup') body = <SignupPage onCreate={() => setAuthed(true)} onSignIn={() => setPub('login')} />;
    else body = <LandingPage onSignIn={() => setPub('login')} onSignUp={() => setPub('signup')} />;
  } else {
    const common = { active: route, onNav: setRoute, onLogout: () => { setAuthed(false); setPub('landing'); } };
    switch (route) {
      case 'webhooks': body = <WebhooksPage {...common} />; break;
      case 'processed-calls': body = <ProcessedCallsPage {...common} />; break;
      case 'workflows': body = <WorkflowsPage {...common} />; break;
      case 'persons': body = <PersonsPage {...common} />; break;
      case 'settings': body = <SettingsPage {...common} />; break;
      default: body = <DashboardPage {...common} />;
    }
  }
  return <ThemeCtx.Provider value={{ theme, toggle }}>{body}</ThemeCtx.Provider>;
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
