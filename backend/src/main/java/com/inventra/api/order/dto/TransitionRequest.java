package com.inventra.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request for order status transition")
public class TransitionRequest {
    
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Schema(description = "Optional notes for the transition", example = "Approved by manager")
    private String notes;
}
