package com.inventra.api.category.dto;

import java.time.Instant;

public record CategoryResponse(
        String id,
        String name,
        String parentId,
        Instant createdAt
) {}
