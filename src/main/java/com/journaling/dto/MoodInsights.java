package com.journaling.dto;

import java.util.List;

public record MoodInsights(
        List<MoodCount> distribution,
        long totalEntries
) {}
