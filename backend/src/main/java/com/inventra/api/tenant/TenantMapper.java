package com.inventra.api.tenant;

import com.inventra.api.entity.Tenant;
import com.inventra.api.tenant.dto.TenantResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);
}
