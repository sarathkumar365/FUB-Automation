package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.persistence.entity.AppUserRole;

/**
 * Result of parsing a verified JWT. Carries only what authorization decisions need;
 * full user lookup is intentionally avoided so JWT validation is a pure function.
 */
public record JwtPrincipal(String username, AppUserRole role) {
}
