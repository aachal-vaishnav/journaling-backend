package com.journaling.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer metrics — exposed at /actuator/prometheus for Grafana.
 *
 * Metrics tracked:
 *  - journaling.entries.created.total   → total entries created (counter)
 *  - journaling.entries.searched.total  → full-text search calls (counter)
 *  - journaling.auth.logins.total       → successful logins (counter)
 *  - journaling.auth.failures.total     → failed login attempts (counter)
 *  - journaling.search.latency          → search query latency (timer)
 *
 * These metrics enable Grafana dashboards showing:
 *  - Requests/sec, error rates, p95 latency
 *  - Entry creation trends
 *  - Auth success/failure ratio (security anomaly detection)
 */
@Component
public class JournalingMetrics {

    private final Counter entriesCreatedCounter;
    private final Counter searchCounter;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;
    private final Timer searchLatencyTimer;
    private final MeterRegistry registry;

    public JournalingMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.entriesCreatedCounter = Counter.builder("journaling.entries.created")
                .description("Total number of journal entries created")
                .register(registry);

        this.searchCounter = Counter.builder("journaling.entries.searched")
                .description("Total number of full-text search queries")
                .register(registry);

        this.authSuccessCounter = Counter.builder("journaling.auth.logins")
                .tag("status", "success")
                .description("Successful login attempts")
                .register(registry);

        this.authFailureCounter = Counter.builder("journaling.auth.logins")
                .tag("status", "failure")
                .description("Failed login attempts")
                .register(registry);

        this.searchLatencyTimer = Timer.builder("journaling.search.latency")
                .description("Full-text search query latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void incrementEntriesCreated() {
        entriesCreatedCounter.increment();
    }

    public void incrementSearch() {
        searchCounter.increment();
    }

    public void incrementAuthSuccess() {
        authSuccessCounter.increment();
    }

    public void incrementAuthFailure() {
        authFailureCounter.increment();
    }

    public void recordSearchLatency(long durationMs) {
        searchLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
