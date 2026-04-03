package com.journaling.dto;

import com.journaling.entity.Mood;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateEntryRequest(
        @Size(min = 1, max = 500, message = "Title must be between 1 and 500 characters")
        String title,

        String content,

        Mood mood,

        List<Long> tagIds
) {}
