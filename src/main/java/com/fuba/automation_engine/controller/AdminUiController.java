package com.fuba.automation_engine.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminUiController {

    @GetMapping({
            "/admin-ui",
            "/admin-ui/",
            "/admin-ui/webhooks",
            "/admin-ui/processed-calls",
            "/admin-ui/workflows",
            "/admin-ui/workflows/new",
            "/admin-ui/workflow-runs",
            "/admin-ui/session-disabled"
    })
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/admin-ui/workflows/{key}",
            "/admin-ui/workflows/{key}/edit",
            "/admin-ui/workflow-runs/{runId}"
    })
    public String detailRoute() {
        return "forward:/index.html";
    }
}
