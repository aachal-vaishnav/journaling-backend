package com.journaling.dto;

import com.journaling.entity.Mood;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EntryRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must be at most 500 characters")
        String title,

        String content,

        @NotNull(message = "Mood is required")
        Mood mood,

        List<Long> tagIds
) {}
