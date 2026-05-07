package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.SettingsConfigResponse;
import com.fuba.automation_engine.service.admin.AdminSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operator view of platform configuration. Powers the Settings
 * page's Configuration tab (see {@code ui/Docs/ui-product-design-proposal.md}
 * lines 202-233). Editable settings (write API + persistence) are a separate
 * future initiative.
 */
@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    public AdminSettingsController(AdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GetMapping("/config")
    public ResponseEntity<SettingsConfigResponse> getConfig() {
        return ResponseEntity.ok(adminSettingsService.currentConfig());
    }
}
