package com.fuba.automation_engine.service.workflow.aicall;

import java.util.Map;

public record GetCallResponse(
        String callSid,
        String status,
        Map<String, Object> terminalPayload) {

    public boolean inProgress() {
        return "in_progress".equals(status);
    }
}
