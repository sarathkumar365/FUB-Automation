# Research

## Purpose
Capture analysis, discovery notes, external references, and assumptions for the `lead-management-platform` feature.

## Current Notes
- Assignment-SLA flow validated as first domain capability.
- Existing repo has strong webhook ingress and adapter foundations.
- Primary architectural gap is generic event routing + durable delayed execution.
- Product direction update (April 17, 2026): `Lead` is treated as the core platform entity and foundation aggregate.
- Foundation persistence increment approved: introduce canonical `leads` table first, then associate lead-scoped actions/workflows/call outcomes to this entity in later phases.
