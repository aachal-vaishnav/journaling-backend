package com.journaling.service;

import com.journaling.dto.*;
import com.journaling.entity.Entry;
import com.journaling.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EntryRepository entryRepository;

    /**
     * Dashboard is cached for 2 minutes (see RedisConfig).
     * All aggregations are computed in MySQL — not in-memory.
     */
    @Cacheable(value = "dashboard", key = "#userId")
    @Transactional(readOnly = true)
    public DashboardStats getDashboard(Long userId) {
        long totalEntries  = entryRepository.countByUserId(userId);
        long totalWords    = entryRepository.sumWordCountByUserId(userId);
        long favoriteCount = entryRepository.countFavoritesByUserId(userId);

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long thisWeekEntries = entryRepository.countByUserIdAndCreatedAtAfter(userId, weekAgo);

        String mostUsedMood = entryRepository.moodDistributionByUserId(userId)
                .stream()
                .findFirst()
                .map(row -> row[0].toString())
                .orElse(null);

        int currentStreak = computeCurrentStreak(userId);

        List<EntryResponse> recentEntries = entryRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(EntryResponse::from)
                .toList();

        return new DashboardStats(
                totalEntries, totalWords, favoriteCount,
                currentStreak, thisWeekEntries, mostUsedMood, recentEntries
        );
    }

    /**
     * Mood insights cached for 10 minutes.
     */
    @Cacheable(value = "analytics", key = "#userId + ':mood:' + #days")
    @Transactional(readOnly = true)
    public MoodInsights getMoodInsights(Long userId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<MoodCount> distribution = entryRepository
                .moodDistributionSince(userId, since)
                .stream()
                .map(row -> new MoodCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();

        long total = distribution.stream().mapToLong(MoodCount::count).sum();
        return new MoodInsights(distribution, total);
    }

    /**
     * Writing streak cached for 10 minutes.
     */
    @Cacheable(value = "analytics", key = "#userId + ':streak'")
    @Transactional(readOnly = true)
    public WritingStreak getWritingStreak(Long userId) {
        int current  = computeCurrentStreak(userId);
        int longest  = computeLongestStreak(userId);
        int totalDays = entryRepository.countDistinctDaysByUserId(userId);

        List<Date> days = entryRepository.findDistinctDaysByUserId(userId);
        String lastDate  = days.isEmpty() ? null : days.get(0).toLocalDate().toString();

        return new WritingStreak(current, longest, totalDays, lastDate);
    }

    // ── Streak helpers ────────────────────────────────────────────────────

    private int computeCurrentStreak(Long userId) {
        List<LocalDate> days = entryRepository.findDistinctDaysByUserId(userId)
                .stream()
                .map(Date::toLocalDate)
                .toList(); // already DESC

        if (days.isEmpty()) return 0;

        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        if (!days.get(0).equals(today) && !days.get(0).equals(yesterday)) return 0;

        int streak = 1;
        for (int i = 1; i < days.size(); i++) {
            if (days.get(i - 1).minusDays(1).equals(days.get(i))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private int computeLongestStreak(Long userId) {
        List<LocalDate> days = entryRepository.findDistinctDaysByUserId(userId)
                .stream()
                .map(Date::toLocalDate)
                .sorted()   // ASC for longest streak calculation
                .toList();

        if (days.isEmpty()) return 0;

        int longest = 1;
        int current = 1;

        for (int i = 1; i < days.size(); i++) {
            if (days.get(i - 1).plusDays(1).equals(days.get(i))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }
}
