import { useNavigate } from 'react-router-dom'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import './LandingPage.css'

const MILESTONES = [
  { className: 'landing-m1', title: uiText.landing.milestones.oneTitle, body: uiText.landing.milestones.oneBody },
  { className: 'landing-m2', title: uiText.landing.milestones.twoTitle, body: uiText.landing.milestones.twoBody },
  { className: 'landing-m3', title: uiText.landing.milestones.threeTitle, body: uiText.landing.milestones.threeBody },
  { className: 'landing-m4', title: uiText.landing.milestones.fourTitle, body: uiText.landing.milestones.fourBody },
  { className: 'landing-m5', title: uiText.landing.milestones.fiveTitle, body: uiText.landing.milestones.fiveBody },
] as const

export function LandingPage() {
  const navigate = useNavigate()

  return (
    <main className="landing-page">
      <section className="landing-wrap" aria-label={uiText.landing.workspaceAriaLabel}>
        <p className="landing-kicker">{uiText.landing.kicker}</p>
        <h1 className="landing-title">{uiText.landing.title}</h1>
        <p className="landing-subtitle">{uiText.landing.subtitle}</p>

        <section className="landing-timeline" aria-label={uiText.landing.timelineAriaLabel}>
          <svg
            className="landing-path"
            viewBox="0 0 1240 420"
            preserveAspectRatio="none"
            role="img"
            aria-label={uiText.landing.timelinePathAriaLabel}
          >
            <defs>
              <linearGradient id="landing-curve-gradient" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor="#67e8f9" />
                <stop offset="100%" stopColor="#2dd4bf" />
              </linearGradient>
            </defs>
            <path
              d="M40,110 C170,30 260,205 350,205 C430,205 475,70 580,70 C670,70 715,220 815,220 C910,220 945,90 1060,90 C1135,90 1175,165 1220,165"
              fill="none"
              stroke="url(#landing-curve-gradient)"
              strokeWidth="4"
              strokeLinecap="round"
              strokeDasharray="3 10"
            />
          </svg>

          {MILESTONES.map((milestone, index) => (
            <article key={milestone.title} data-testid="landing-milestone" className={`landing-milestone ${milestone.className}`}>
              <span className="landing-milestone-tag">
                {uiText.landing.milestoneTagPrefix} {String(index + 1).padStart(2, '0')}
              </span>
              <h2 className="landing-milestone-title">{milestone.title}</h2>
              <p className="landing-milestone-body">{milestone.body}</p>
            </article>
          ))}
        </section>

        <p className="landing-footer-note">{uiText.landing.footerNote}</p>

        <div className="landing-cta">
          <Button size="sm" onClick={() => navigate(routes.webhooks)}>
            {uiText.landing.primaryAction}
          </Button>
          <Button variant="outline" size="sm" onClick={() => navigate(routes.processedCalls)}>
            {uiText.landing.secondaryAction}
          </Button>
        </div>
      </section>
    </main>
  )
}
