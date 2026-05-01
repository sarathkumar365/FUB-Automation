# RD-004: Admin authentication uses DB-backed users + JWT bearer tokens

## Status
Accepted

## Context
Until Phase 2 of `dev-hosting-security-hardening`, every `/admin/**` endpoint was anonymous because there was no auth layer at all. We needed a baseline that:
- works for a single-admin dev host today,
- supports multiple users with different permissions tomorrow,
- doesn't require operator-side state (no Redis, no session DB),
- doesn't add CSRF complexity to the SPA, and
- gives a clean foundation for any future auth feature in this repo (admin user CRUD, password reset, audit log, etc.).

## Decision
Admin authentication for this repository is **DB-backed users + JWT bearer tokens**.

Locked V1 decisions:
- Users live in the `app_user` table (Flyway `V17`). Passwords are BCrypt-hashed (cost 12).
- Roles are an enum: `ADMIN`, `OPERATOR`, `VIEWER`. Enforced per endpoint via `@PreAuthorize`.
- Token format is JWT, HS256, issued by the server's `JwtService`. Claims: `sub` (username), `role`, `iss`, `iat`, `exp`. No refresh tokens.
- Signing key comes from `JWT_SECRET` (≥32 chars). Outside the `local` Spring profile a blank key fails startup; inside `local` an ephemeral random key is used with a `WARN` so dev-loop ergonomics aren't ruined.
- Spring Security runs `STATELESS` with CSRF disabled. No session cookies, no form login, no HTTP Basic.
- The first ADMIN user is seeded on first boot from `ADMIN_AUTH_USERNAME` / `ADMIN_AUTH_PASSWORD` env vars. The seeder is one-shot — it never modifies an existing user.
- `/webhooks/**` and `/health` remain anonymous (FUB delivers without credentials; platform health probes need open access). The JWT filter explicitly skips both via `shouldNotFilter`.
- Test infrastructure: `MockMvcSecurityTestConfig` re-applies the `springSecurity()` MockMvc configurer that Spring Boot 4 stopped auto-applying. Required for `@WithMockUser` to propagate into MockMvc requests.

## Impact
- Any new admin-facing endpoint inherits the auth layer for free; the only decision per endpoint is which roles can call it (`@PreAuthorize`).
- Future auth-related work (refresh tokens, MFA, login attempt rate-limiting, account lockout, user CRUD endpoints) builds on this same `app_user` table and `JwtService`. Don't add a parallel auth scheme.
- SPA stores the token in `sessionStorage` and sends `Authorization: Bearer <jwt>`. No CSRF token handling on the front-end side.
- Token revocation is best-effort (TTL-based). If immediate revocation becomes a requirement, introduce a denylist or shorten TTL — do not switch to sessions.

## Applies To
- Repo-wide
- Every authenticated endpoint under `/admin/**`
- Any future admin tooling, user-management UI, password reset / rotation features

## Supersedes / Superseded By
- Supersedes: none (no prior auth scheme)
- Superseded by: none

## See Also
- Feature plan: [`Docs/features/dev-hosting-security-hardening/plan.md`](../features/dev-hosting-security-hardening/plan.md) — full design with sequence diagram and role matrix.
- Phase log: [`Docs/features/dev-hosting-security-hardening/phase-2-implementation.md`](../features/dev-hosting-security-hardening/phase-2-implementation.md) — decisions taken during implementation.
- Security checklist: [`Docs/hosting-decision/dev/dev-hosting-security-checklist.md`](../hosting-decision/dev/dev-hosting-security-checklist.md) — origin of the A1 requirement.
