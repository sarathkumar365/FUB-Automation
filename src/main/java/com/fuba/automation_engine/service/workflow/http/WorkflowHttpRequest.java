package com.fuba.automation_engine.service.workflow.http;

import java.util.Map;

public record WorkflowHttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        String body) {
}

