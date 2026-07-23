package com.inventra.api.customer.dto;

import com.inventra.api.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Customer information")
public class CustomerResponse {
    
    @Schema(description = "Customer unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;
    
    @Schema(description = "Customer name", example = "Acme Corporation")
    private String name;
    
    @Schema(description = "Customer email address", example = "contact@acme.com")
    private String email;
    
    @Schema(description = "Customer phone number", example = "+1-555-0123")
    private String phone;
    
    @Schema(description = "Customer address", example = "123 Main St, Springfield, IL 62701")
    private String address;
    
    @Schema(description = "Additional notes about the customer", example = "Preferred customer, net 30 payment terms")
    private String notes;
    
    @Schema(description = "Customer status", example = "ACTIVE")
    private CustomerStatus status;
    
    @Schema(description = "Timestamp when customer was created")
    private Instant createdAt;
    
    @Schema(description = "Timestamp when customer was last updated")
    private Instant updatedAt;
}
