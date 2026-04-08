package com.fuba.automation_engine.service.model;

public record PersonCommunicationCheckResult(
        Long personId,
        boolean communicationFound) {
}
