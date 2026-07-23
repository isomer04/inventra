package com.inventra.api.customer;

import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.customer.dto.CustomerResponse;
import com.inventra.api.customer.dto.UpdateCustomerRequest;
import com.inventra.api.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customers", description = "Customer management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {
    
    private final CustomerService customerService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(
        summary = "List customers",
        description = "Get a paginated list of customers with optional search and status filtering. All roles can access."
    )
    @ApiResponse(responseCode = "200", description = "Customers retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @Parameter(description = "Search term for name or email")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by customer status")
            @RequestParam(required = false) CustomerStatus status,
            
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.debug("GET /api/v1/customers - search: {}, status: {}, page: {}", LogSanitizer.sanitize(search), LogSanitizer.sanitize(status), pageable.getPageNumber());
        Page<CustomerResponse> customers = customerService.getAllCustomers(search, status, pageable);
        return ResponseEntity.ok(customers);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(
        summary = "Get customer by ID",
        description = "Retrieve a single customer by their ID. All roles can access."
    )
    @ApiResponse(responseCode = "200", description = "Customer retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    public ResponseEntity<CustomerResponse> getCustomerById(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable String id
    ) {
        log.debug("GET /api/v1/customers/{}", LogSanitizer.sanitize(id));
        CustomerResponse customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(customer);
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(
        summary = "Create customer",
        description = "Create a new customer. Requires ADMIN or MANAGER role."
    )
    @ApiResponse(responseCode = "201", description = "Customer created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        log.info("POST /api/v1/customers - creating customer");
        CustomerResponse customer = customerService.createCustomer(request);
        log.info("POST /api/v1/customers - customer created with ID: {}", LogSanitizer.sanitize(customer.getId()));
        log.debug("Created customer: {}", LogSanitizer.sanitize(request.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(
        summary = "Update customer",
        description = "Update an existing customer. Requires ADMIN or MANAGER role."
    )
    @ApiResponse(responseCode = "200", description = "Customer updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    public ResponseEntity<CustomerResponse> updateCustomer(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable String id,
            
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        log.info("PUT /api/v1/customers/{}", LogSanitizer.sanitize(id));
        CustomerResponse customer = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(customer);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete customer",
        description = "Delete a customer. Requires ADMIN role. Cannot delete if customer has orders."
    )
    @ApiResponse(responseCode = "204", description = "Customer deleted successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Customer has existing orders", content = @Content)
    public ResponseEntity<Void> deleteCustomer(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable String id
    ) {
        log.info("DELETE /api/v1/customers/{}", LogSanitizer.sanitize(id));
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
