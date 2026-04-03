package com.journaling.dto;

import java.util.List;

public record DashboardStats(
        long totalEntries,
        long totalWords,
        long favoriteCount,
        int currentStreak,
        long thisWeekEntries,
        String mostUsedMood,
        List<EntryResponse> recentEntries
) {}
