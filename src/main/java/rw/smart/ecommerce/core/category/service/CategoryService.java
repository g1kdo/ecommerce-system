package rw.smart.ecommerce.core.category.service;

import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    CategoryResponse findById(Long id);

    List<CategoryResponse> findAll();

    void delete(Long id);
}
