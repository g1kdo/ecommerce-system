package rw.smart.ecommerce.core.category.service;

import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    CategoryResponse findById(Long id);

    List<CategoryResponse> findAll();

    /**
     * Paginated, optionally keyword-filtered listing for the administrator's
     * category screen. {@link #findAll()} stays as it is: the storefront
     * navigation wants every category in one cached list, not a page of them.
     */
    PageResponse<CategoryResponse> search(String keyword, Integer page, Integer size,
                                          String sortBy, String direction);

    void delete(Long id);
}
