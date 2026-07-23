package com.inventra.api.auth.dto;

import com.inventra.api.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100, message = "Tenant name must not exceed 100 characters") String tenantName,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase alphanumeric with hyphens")
        @Size(max = 50, message = "Slug must not exceed 50 characters") String slug,
        @NotBlank @Email @Size(max = 150, message = "Email must not exceed 150 characters") String email,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72,
               message = "password must not exceed 72 bytes when UTF-8 encoded") String password,
        @Size(max = 80, message = "First name must not exceed 80 characters") String firstName,
        @Size(max = 80, message = "Last name must not exceed 80 characters") String lastName
) {}
