package com.inventra.api.tenant.dto;

import com.inventra.api.entity.TenantStatus;

import java.time.Instant;

public record TenantResponse(
        String id,
        String name,
        String slug,
        TenantStatus status,
        Instant createdAt
) {}
