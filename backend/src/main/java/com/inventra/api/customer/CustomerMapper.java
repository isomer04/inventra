package com.inventra.api.customer;

import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.customer.dto.CustomerResponse;
import com.inventra.api.customer.dto.UpdateCustomerRequest;
import com.inventra.api.entity.Customer;
import org.mapstruct.*;

/**
 * MapStruct mapper for converting between Customer entity and DTOs.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(CreateCustomerRequest request);
    
    /**
     * Only non-null fields from the request are applied to the existing entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateCustomerRequest request, @MappingTarget Customer customer);
    
    CustomerResponse toResponse(Customer customer);
}
