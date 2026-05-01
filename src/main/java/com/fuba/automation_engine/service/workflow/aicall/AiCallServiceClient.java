package com.fuba.automation_engine.service.workflow.aicall;

public interface AiCallServiceClient {

    PlaceCallResponse placeCall(PlaceCallRequest request);

    GetCallResponse getCall(String callSid);
}
