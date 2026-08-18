package rw.smart.ecommerce.core.product.service;

import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.product.dto.ProductFilter;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.LowStockResponse;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.RelatedProductResponse;

import java.util.List;

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

    /**
     * The "customers also bought" strip: products that appear in the same orders
     * as this one, most frequently paired first.
     *
     * Derived from order history rather than the catalogue, which is why editing
     * a product does not invalidate it.
     */
    List<RelatedProductResponse> findRelated(Long id, Integer limit);

    void delete(Long id);
}
