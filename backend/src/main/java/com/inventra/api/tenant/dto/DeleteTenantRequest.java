package com.inventra.api.tenant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for tenant self-deletion (right-to-erasure).
 *
 * <p>Requires the tenant slug as confirmation to prevent accidental deletion.
 * The caller must supply the exact slug of their own tenant.
 */
public record DeleteTenantRequest(
        @NotBlank(message = "Confirm your tenant slug to proceed with deletion")
        String confirmSlug
) {}
