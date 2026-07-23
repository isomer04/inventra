package com.inventra.api.product;

import com.inventra.api.entity.ProductStatus;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.product.dto.ProductResponse;
import com.inventra.api.product.dto.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Products", description = "Product management endpoints")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    // Explicit annotation — all authenticated roles may list products.
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "List all products", description = "Returns paginated products with optional filters")
    public Page<ProductResponse> getAll(
            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) String categoryId,

            @Parameter(description = "Filter by status (ACTIVE, DISCONTINUED)")
            @RequestParam(required = false) ProductStatus status,

            @Parameter(description = "Search in product name or SKU")
            @RequestParam(required = false) String search,

            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return productService.getAll(categoryId, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get product by ID")
    public ProductResponse getById(@PathVariable String id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new product", description = "ADMIN and MANAGER only")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        return productService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update a product", description = "ADMIN and MANAGER only")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete a product", description = "ADMIN only. Sets status to DISCONTINUED")
    public void delete(@PathVariable String id) {
        productService.delete(id);
    }
}
