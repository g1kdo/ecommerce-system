package rw.smart.ecommerce.core.product.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.core.product.dto.ProductFilter;
import rw.smart.ecommerce.core.product.dto.ProductFilterInput;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.core.report.dto.ReportDtos.RelatedProductResponse;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.util.List;

/**
 * GraphQL entry points for the catalogue.
 *
 * These call the same services the REST controllers do — GraphQL is an
 * alternative transport, not a second implementation. What it buys the client is
 * field selection: a mobile list view can ask for {@code id, name, price} and
 * skip the description entirely.
 *
 * Note the absence of {@code StandardResponse}: GraphQL already defines its own
 * envelope ({@code data} / {@code errors}), so wrapping again would nest two
 * competing error channels.
 */
@Controller
public class ProductGraphQLController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    public ProductGraphQLController(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @QueryMapping
    public PageResponse<ProductResponse> products(@Argument ProductFilterInput filter) {
        ProductFilterInput effective = filter == null ? ProductFilterInput.defaults() : filter;

        ProductFilter criteria = new ProductFilter(
                effective.keyword(), effective.categoryId(), effective.minPrice(), effective.maxPrice());

        return productService.browse(criteria, effective.page(), effective.size(),
                effective.sortBy(), effective.direction());
    }

    @QueryMapping
    public ProductResponse product(@Argument Long id) {
        return productService.findById(id);
    }

    /**
     * A field on {@code Product}, not a root query.
     *
     * It belongs to a product, and as a field it costs nothing when the client
     * does not select it — the same argument that made {@code reviewSummary} safe
     * to put on the type. The REST equivalent is a second endpoint that a client
     * has to know to call.
     *
     * Unlike {@code reviewSummary} this is not batched. A catalogue page selecting
     * it for twenty products would run twenty self-joins, so it is meant for a
     * product detail view. A DataLoader here would need the bought-together query
     * rewritten to group over a set of anchor products, which is worth doing only
     * once something actually asks for it that way.
     */
    @SchemaMapping(typeName = "Product")
    public List<RelatedProductResponse> relatedProducts(ProductResponse product, @Argument Integer limit) {
        return productService.findRelated(product.id(), limit);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public ProductResponse createProduct(@Argument @Valid ProductRequest input) {
        return productService.create(input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public ProductResponse updateProduct(@Argument Long id, @Argument @Valid ProductRequest input) {
        return productService.update(id, input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public boolean deleteProduct(@Argument Long id) {
        productService.delete(id);
        return true;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public int adjustStock(@Argument Long productId, @Argument int quantity) {
        return inventoryService.setStock(productId, quantity);
    }
}
