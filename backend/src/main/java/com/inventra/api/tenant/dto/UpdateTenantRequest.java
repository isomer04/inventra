package com.inventra.api.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(max = 100, message = "Tenant name must not exceed 100 characters") String name
) {}
