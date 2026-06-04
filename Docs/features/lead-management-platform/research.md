# Research

> ⚠️ **Historical (2026-06-03).** Predates the Lead→Person rename (V21) and the policy-engine removal (V12). Kept as research history; "lead" framing throughout is now "person".

## Purpose
Capture analysis, discovery notes, external references, and assumptions for the `lead-management-platform` feature.

## Current Notes
- Assignment-SLA flow validated as first domain capability.
- Existing repo has strong webhook ingress and adapter foundations.
- Primary architectural gap is generic event routing + durable delayed execution.
- Product direction update (April 17, 2026): `Lead` is treated as the core platform entity and foundation aggregate.
- Foundation persistence increment approved: introduce canonical `leads` table first, then associate lead-scoped actions/workflows/call outcomes to this entity in later phases.
