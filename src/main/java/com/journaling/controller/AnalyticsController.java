package com.journaling.controller;

import com.journaling.dto.DashboardStats;
import com.journaling.dto.MoodInsights;
import com.journaling.dto.WritingStreak;
import com.journaling.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> dashboard(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getDashboard(userId(userDetails)));
    }

    @GetMapping("/mood-insights")
    public ResponseEntity<MoodInsights> moodInsights(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(analyticsService.getMoodInsights(userId(userDetails), days));
    }

    @GetMapping("/writing-streak")
    public ResponseEntity<WritingStreak> writingStreak(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getWritingStreak(userId(userDetails)));
    }

    private Long userId(UserDetails userDetails) {
        return Long.parseLong(userDetails.getUsername());
    }
}
