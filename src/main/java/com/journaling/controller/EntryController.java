package com.journaling.controller;

import com.journaling.dto.*;
import com.journaling.service.AnalyticsAsyncService;
import com.journaling.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;
    private final AnalyticsAsyncService analyticsAsyncService;

    @GetMapping
    public ResponseEntity<PagedResponse<EntryResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String mood,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Long userId = userId(userDetails);
        return ResponseEntity.ok(entryService.listEntries(userId, search, mood, page, limit));
    }

    @PostMapping
    public ResponseEntity<EntryResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EntryRequest request
    ) {
        Long userId = userId(userDetails);
        EntryResponse entry = entryService.createEntry(userId, request);

        // Fire-and-forget async tasks — runs on virtual thread, does not block response
        analyticsAsyncService.refreshDashboardCache(userId);
        analyticsAsyncService.recordMoodEvent(userId, entry.mood().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntryResponse> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(entryService.getEntry(userId(userDetails), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EntryResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEntryRequest request
    ) {
        Long userId = userId(userDetails);
        EntryResponse entry = entryService.updateEntry(userId, id, request);
        analyticsAsyncService.refreshDashboardCache(userId);
        return ResponseEntity.ok(entry);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        Long userId = userId(userDetails);
        entryService.deleteEntry(userId, id);
        analyticsAsyncService.refreshDashboardCache(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<EntryResponse> toggleFavorite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(entryService.toggleFavorite(userId(userDetails), id));
    }

    private Long userId(UserDetails userDetails) {
        return Long.parseLong(userDetails.getUsername());
    }
}
