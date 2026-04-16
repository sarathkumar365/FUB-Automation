# Dev-Phase Hosting Comparison (Internal Tool)

Date: 2026-04-14

## Scope and Requirements
- App stage: development phase only
- Usage: internal tool
- Priorities: lowest cost, simple setup, reliable webhook ingestion
- Not in scope right now: scaling strategy, multi-region availability
- Stack requirements:
  - Spring Boot backend (always-on webhook endpoint)
  - PostgreSQL database
  - React/Vite admin frontend
  - SSE/live updates for admin UI

## Providers Compared
- Railway
- Render
- Amazon Web Services (AWS)
- Microsoft Azure

## Summary Analysis
- Railway and Render both satisfy the core technical requirements for this app.
- AWS and Azure can also satisfy requirements, but usually introduce higher setup complexity and higher baseline cost for this stage.
- For this specific dev/internal phase, lowest-cost operation with acceptable platform limits is the deciding factor.

## Cost-Oriented Decision
- Winner: **Railway**

Why Railway wins for this phase:
- Lowest practical entry cost profile for always-on app + small Postgres in dev.
- Fast deployment path for Spring Boot + Postgres + env-based config.
- Supports required runtime behavior (public webhook endpoint, SSE-compatible behavior with heartbeat).
- Good enough operational controls for an internal tool without over-investing in infrastructure now.

## Notes and Tradeoffs
- Render remains a strong alternative if we later prefer more fixed-tier, predictable billing.
- AWS/Azure remain good learning and long-term platform options, but are not the cheapest fit for current scope.
- Re-evaluate hosting when requirements include production reliability targets, higher traffic, or multi-region.

## Final Recommendation
Use **Railway** for current dev-phase hosting.
