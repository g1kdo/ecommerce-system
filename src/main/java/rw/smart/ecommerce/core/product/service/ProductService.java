package rw.smart.ecommerce.core.product.service;

import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.product.dto.ProductFilter;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.LowStockResponse;
import rw.smart.ecommerce.core.product.dto.ProductResponse;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse findById(Long id);

    /**
     * The customer catalogue query: search, multi-criteria filter, sort and page
     * in one call. {@code filter} may be entirely empty.
     */
    PageResponse<ProductResponse> browse(ProductFilter filter, Integer page, Integer size,
                                         String sortBy, String direction);

    /**
     * Reorder report: everything at or below {@code threshold} units, lowest
     * first. Paged rather than listed — the whole point of running it is that
     * nobody knows in advance how many products are short.
     */
    PageResponse<LowStockResponse> findLowStock(Integer threshold, Integer page, Integer size);

    void delete(Long id);
}
