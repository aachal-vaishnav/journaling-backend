package com.journaling.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous analytics tasks — run on virtual-thread executor (analyticsExecutor).
 *
 * These tasks execute after the HTTP response is sent, so the user never waits for
 * them. Each task is backed by a Java 21 virtual thread — no platform thread is
 * blocked while awaiting I/O (cache writes, DB reads).
 *
 * Typical use cases:
 *  - Warm-up Redis caches after an entry is written
 *  - Precompute daily summaries for the next dashboard load
 *  - Emit custom analytics events without delaying the primary request
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsAsyncService {

    private final AnalyticsService analyticsService;
    private final CacheManager cacheManager;

    /**
     * Invalidates and pre-warms the dashboard cache after a write.
     * Runs on a virtual thread — non-blocking from the caller's perspective.
     */
    @Async("analyticsExecutor")
    public void refreshDashboardCache(Long userId) {
        try {
            var cache = cacheManager.getCache("dashboard");
            if (cache != null) cache.evict(userId);

            // Pre-warm immediately so the next dashboard request is a cache hit
            analyticsService.getDashboard(userId);
            log.debug("Dashboard cache refreshed for user {}", userId);
        } catch (Exception ex) {
            log.warn("Async dashboard refresh failed for user {}: {}", userId, ex.getMessage());
        }
    }

    /**
     * Logs a mood event for future trend analysis.
     * In a production system this would publish to Kafka/SQS.
     */
    @Async("analyticsExecutor")
    public void recordMoodEvent(Long userId, String mood) {
        log.info("Mood event recorded — user={} mood={}", userId, mood);
        // Future: publish to Kafka topic for real-time mood trend streaming
    }

    /**
     * Generates a daily writing summary — simulates a background job
     * that could be triggered via @Scheduled or a Kafka consumer.
     */
    @Async("analyticsExecutor")
    public void generateDailySummary(Long userId) {
        log.info("Generating daily summary for user {}", userId);
        try {
            var streak  = analyticsService.getWritingStreak(userId);
            var insights = analyticsService.getMoodInsights(userId, 7);
            log.info("Daily summary — user={} streak={} entries_this_week={}",
                    userId, streak.currentStreak(), insights.totalEntries());
        } catch (Exception ex) {
            log.warn("Daily summary failed for user {}: {}", userId, ex.getMessage());
        }
    }
}
