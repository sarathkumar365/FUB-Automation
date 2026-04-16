package com.fuba.automation_engine.service.workflow.http;

import java.util.List;
import java.util.Map;

public record WorkflowHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body) {
}

