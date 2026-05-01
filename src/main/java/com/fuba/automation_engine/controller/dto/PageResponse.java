package com.fuba.automation_engine.controller.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total) {
}
