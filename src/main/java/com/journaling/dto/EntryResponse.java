package com.journaling.dto;

import com.journaling.entity.Entry;
import com.journaling.entity.Mood;

import java.time.Instant;
import java.util.List;

public record EntryResponse(
        Long id,
        Long userId,
        String title,
        String content,
        Mood mood,
        boolean isFavorite,
        int wordCount,
        List<TagResponse> tags,
        Instant createdAt,
        Instant updatedAt
) {
    public static EntryResponse from(Entry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getUser().getId(),
                entry.getTitle(),
                entry.getContent(),
                entry.getMood(),
                entry.isFavorite(),
                entry.getWordCount(),
                entry.getTags().stream().map(TagResponse::from).toList(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
