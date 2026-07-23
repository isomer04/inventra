package com.inventra.api.category;

import com.inventra.api.category.dto.CategoryResponse;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.category.dto.UpdateCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Categories", description = "Category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    // Explicit annotation — all authenticated roles have read access.
    // Previously this was unannotated — relying on anyRequest().authenticated() in SecurityConfig,
    // which is correct but invisible to future developers reading this controller.
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "List all categories", description = "Returns all categories for the authenticated tenant")
    public List<CategoryResponse> getAll() {
        return categoryService.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get category by ID")
    public CategoryResponse getById(@PathVariable String id) {
        return categoryService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new category", description = "ADMIN and MANAGER only")
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest req) {
        return categoryService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update a category", description = "ADMIN and MANAGER only")
    public CategoryResponse update(@PathVariable String id, @Valid @RequestBody UpdateCategoryRequest req) {
        return categoryService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a category", description = "ADMIN only. Fails if products are linked to this category")
    public void delete(@PathVariable String id) {
        categoryService.delete(id);
    }
}
