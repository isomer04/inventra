package com.inventra.api.user.dto;

import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;

import java.time.Instant;

public record UserResponse(
        String id,
        String tenantId,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
