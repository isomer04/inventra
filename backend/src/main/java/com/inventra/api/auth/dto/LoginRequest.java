package com.inventra.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Align password constraint with RegisterRequest.
// @Size(min=8) rejects obviously short inputs before BCrypt runs,
// consistent with the min-8 rule enforced at registration.
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {}
