package com.flux.service.workflow.cortex;

public interface CortexClient {

    PlaceCallResponse placeCall(PlaceCallRequest request);

    GetCallResponse getCall(String callSid);
}
