package com.journaling.dto;

public record WritingStreak(
        int currentStreak,
        int longestStreak,
        int totalDaysWritten,
        String lastEntryDate
) {}
