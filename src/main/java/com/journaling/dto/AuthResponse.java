package com.journaling.dto;

public record AuthResponse(
        String token,
        UserProfile user
) {}
