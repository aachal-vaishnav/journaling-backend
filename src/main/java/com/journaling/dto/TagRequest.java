package com.journaling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "Tag name is required")
        @Size(max = 100, message = "Tag name must be at most 100 characters")
        String name,

        String color
) {}
