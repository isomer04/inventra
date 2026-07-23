package com.inventra.api.user;

import com.inventra.api.entity.User;
import com.inventra.api.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "tenant.id", target = "tenantId")
    UserResponse toResponse(User user);
}
