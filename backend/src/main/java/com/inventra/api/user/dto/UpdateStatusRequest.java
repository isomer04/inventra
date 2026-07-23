package com.inventra.api.user.dto;

import com.inventra.api.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull UserStatus status) {}
