package com.journaling.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> entries,
        long total,
        int page,
        int limit,
        int totalPages
) {}
