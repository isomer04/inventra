package com.inventra.api.user.dto;

import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 80, message = "First name must not exceed 80 characters") String firstName,
        @Size(max = 80, message = "Last name must not exceed 80 characters") String lastName,
        @Size(min = 8, message = "password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72,
              message = "password must not exceed 72 bytes when UTF-8 encoded") String password,
        /**
         * Required when a user changes their own password; ignored for admin-initiated
         * resets of another user's password. See {@code UserService.update}.
         */
        String currentPassword,
        UserRole role,
        UserStatus status
) {}
