# Reporting Phase 2 — Design Handoff

> Brief for the designer. **Functional & content requirements only** — no real data values, and no
> layout / chart-type / component direction. Use the Flux Design System foundations and design freely.
> Build target + data semantics: [phase-2-implementation.md](./phase-2-implementation.md).

## Product context
Flux automates call handling over a real-estate brokerage's CRM. These two screens are **management
reports** for the brokerage owner / team manager (admin) — **not** the agents themselves.

## Global behavior (both screens)
- A **time-window control** with two settings: *last 24 hours* and *last 7 days*. It filters everything.
- Read-only. Design for three states: **loading**, **empty** (nothing in the window), **populated**.
- Data shape to design for: many distinct lead sources; a modest set of agents; leads-per-agent ranging
  from a handful to many.

## Screen 1 — Lead source → holding agent → contact outcome
- **Answers:** where do our leads come from, who holds them, and are they being reached?
- A **three-level structure** the manager moves through:
  1. **Lead source** — the origin a lead came in from.
  2. Within a source — the **agents currently holding** those leads.
  3. Within an agent — the **contact outcome** of those leads.
- **Contact outcome** resolves to three exclusive categories: **reached by call**, **reached by another
  channel**, **not yet reached**.
- Each level conveys both a **volume** and a **share** — the manager compares sources and agents against
  one another.

## Screen 2 — Agent accountability
- **Answers:** for each agent, of the leads they're responsible for, how many have they engaged — and how?
- Organized **per agent**; the manager compares and ranks agents.
- Each agent's leads split into the same three categories: reached by that agent's **call**, reached by
  **another channel**, and **not yet reached**.
- The manager can **open an agent** to see the actual list of their *not-yet-reached* leads — a worklist
  with enough per-lead identity to act on (**name** and a **contact handle**). Treat that as sensitive
  contact data.

## Out of scope for design
- No real data values are provided on purpose — design for realistic ranges.
- Conversion / deal outcomes are **not** part of these screens.
- No layout, chart-type, or component direction is prescribed.

## Deliverable expected back
Screen designs for both reports — including the drill interactions and the loading / empty / populated
states — on the Flux Design System foundations.
