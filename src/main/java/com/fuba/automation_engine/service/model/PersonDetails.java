package com.fuba.automation_engine.service.model;

public record PersonDetails(
        Long id,
        Boolean claimed,
        Long assignedUserId) {
}
