package com.inventra.api.user;

import com.inventra.api.entity.User;
import com.inventra.api.user.dto.CreateUserRequest;
import com.inventra.api.user.dto.UpdateStatusRequest;
import com.inventra.api.user.dto.UpdateUserRequest;
import com.inventra.api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management within the caller's tenant")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List all users in the caller's tenant")
    public List<UserResponse> getAll() {
        return userService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user in the caller's tenant")
    public UserResponse create(@Valid @RequestBody CreateUserRequest req,
                               @AuthenticationPrincipal User currentUser) {
        return userService.create(req, currentUser);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get a user by ID (ADMIN/MANAGER or self)")
    public UserResponse getById(@PathVariable String id,
                                @AuthenticationPrincipal User currentUser) {
        return userService.getById(id, currentUser);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Update a user (ADMIN can update all fields; self can update name and password)")
    public UserResponse update(@PathVariable String id,
                               @Valid @RequestBody UpdateUserRequest req,
                               @AuthenticationPrincipal User currentUser) {
        return userService.update(id, req, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user (ADMIN only, cannot self-delete)")
    public void delete(@PathVariable String id,
                       @AuthenticationPrincipal User currentUser) {
        userService.delete(id, currentUser);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a user (ADMIN only, cannot self-deactivate)")
    public UserResponse updateStatus(@PathVariable String id,
                                     @Valid @RequestBody UpdateStatusRequest req,
                                     @AuthenticationPrincipal User currentUser) {
        return userService.updateStatus(id, req, currentUser);
    }
}
