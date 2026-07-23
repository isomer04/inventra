package com.inventra.api.category;

import com.inventra.api.category.dto.CategoryResponse;
import com.inventra.api.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toResponse(Category category);
}
