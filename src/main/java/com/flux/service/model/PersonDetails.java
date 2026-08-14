package com.flux.service.model;

public record PersonDetails(
        Long id,
        Boolean claimed,
        Long assignedUserId,
        Integer contacted) {
}
