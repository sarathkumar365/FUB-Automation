package com.flux.controller;

import com.flux.controller.dto.DashboardSnapshotDto;
import com.flux.service.reporting.dashboard.DashboardSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class DashboardController {

    private final DashboardSnapshotService snapshotService;

    public DashboardController(DashboardSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<DashboardSnapshotDto> snapshot() {
        return ResponseEntity.ok(snapshotService.snapshot());
    }
}
