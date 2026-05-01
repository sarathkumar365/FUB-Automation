package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.service.auth.AdminAuthService;
import com.fuba.automation_engine.service.auth.JwtService;
import com.fuba.automation_engine.service.auth.JwtService.IssuedToken;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AdminAuthService adminAuthService;
    private final JwtService jwtService;

    public AdminAuthController(
            AuthenticationManager authenticationManager,
            AdminAuthService adminAuthService,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.adminAuthService = adminAuthService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            return invalidCredentials();
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException ex) {
            log.warn("Failed login attempt username={} reason={}", request.username(), ex.getClass().getSimpleName());
            return invalidCredentials();
        }

        Optional<AppUserEntity> userOpt = adminAuthService.findActiveByUsername(request.username());
        if (userOpt.isEmpty()) {
            // Authentication succeeded but the row was disabled / deleted between checks.
            return invalidCredentials();
        }
        AppUserEntity user = userOpt.get();
        IssuedToken issued = jwtService.issue(user);
        adminAuthService.recordLogin(user.getId());
        return ResponseEntity.ok(new LoginResponse(
                issued.token(),
                "Bearer",
                issued.expiresAt(),
                user.getUsername(),
                user.getRole()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        String role = auth.getAuthorities().stream()
                .map(Object::toString)
                .filter(name -> name.startsWith("ROLE_"))
                .map(name -> name.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
        return ResponseEntity.ok(Map.of(
                "username", auth.getName(),
                "role", role));
    }

    private ResponseEntity<?> invalidCredentials() {
        return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            String tokenType,
            Instant expiresAt,
            String username,
            AppUserRole role) {
    }
}
