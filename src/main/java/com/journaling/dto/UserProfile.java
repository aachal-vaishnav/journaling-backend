package com.journaling.dto;

import com.journaling.entity.User;

import java.time.Instant;

public record UserProfile(
        Long id,
        String name,
        String email,
        Instant createdAt
) {
    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
