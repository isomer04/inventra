package com.inventra.api.customer.dto;

import com.inventra.api.entity.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * All fields are optional - only provided (non-null) fields will be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update an existing customer")
public class UpdateCustomerRequest {
    
    @Size(min = 1, max = 200, message = "Customer name must be between 1 and 200 characters")
    @Pattern(regexp = ".*\\S.*", message = "Customer name must contain non-whitespace characters")
    @Schema(description = "Customer name", example = "Acme Corporation")
    private String name;
    
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Customer email address", example = "contact@acme.com")
    private String email;
    
    @Size(max = 30, message = "Phone must not exceed 30 characters")
    @Schema(description = "Customer phone number", example = "+1-555-0123")
    private String phone;
    
    @Schema(description = "Customer address", example = "123 Main St, Springfield, IL 62701")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
    
    @Schema(description = "Additional notes about the customer", example = "Preferred customer, net 30 payment terms")
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
    
    @Schema(description = "Customer status", example = "ACTIVE")
    private CustomerStatus status;
}
