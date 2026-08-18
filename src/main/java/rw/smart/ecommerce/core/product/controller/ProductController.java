package rw.smart.ecommerce.core.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.inventory.dto.StockRequest;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.core.product.dto.LowStockResponse;
import rw.smart.ecommerce.core.product.dto.ProductFilter;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.core.report.dto.ReportDtos.RelatedProductResponse;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One resource, one controller.
 *
 * The previous split into an admin controller and a customer controller meant
 * two URLs for the same product, two places to change when the shape of a
 * product changed, and a reader who had to know which file to open. The resource
 * is the same either way; only the permission differs, and that is now stated
 * per method by {@code @PreAuthorize} rather than encoded in the path.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Catalogue browsing (public) and product management (admin)")
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    public ProductController(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    // ---------------- Public catalogue ----------------

    @Operation(summary = "Search, filter, sort and paginate the product catalogue",
            description = """
                    All parameters are optional; omitting them returns the first page of the
                    whole catalogue. `sortBy` accepts id, name, price, createdAt or sku -
                    anything else is rejected with 400 rather than surfacing a server error.""")
    @GetMapping
    public ResponseEntity<StandardResponse<PageResponse<ProductResponse>>> browse(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {

        ProductFilter filter = new ProductFilter(keyword, categoryId, minPrice, maxPrice);
        PageResponse<ProductResponse> results = productService.browse(filter, page, size, sortBy, direction);

        String message = results.totalElements() == 0
                ? "No products matched your search"
                : results.totalElements() + " product(s) found";

        return ResponseEntity.ok(StandardResponse.ok(message, results));
    }

    @Operation(summary = "Get a single product by id")
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<ProductResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(StandardResponse.ok("Product retrieved successfully", productService.findById(id)));
    }

    @Operation(summary = "Products frequently bought with this one",
            description = """
                    Built from order history, not from the catalogue: a product appears here
                    because customers put both in the same order. Cancelled orders are excluded.""")
    @GetMapping("/{id}/related")
    public ResponseEntity<StandardResponse<List<RelatedProductResponse>>> related(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit) {

        List<RelatedProductResponse> related = productService.findRelated(id, limit);

        String message = related.isEmpty()
                ? "This product has not yet been bought alongside anything else"
                : related.size() + " related product(s) found";

        return ResponseEntity.ok(StandardResponse.ok(message, related));
    }

    // ---------------- Administration ----------------

    @Operation(summary = "Products at or below a stock threshold (Admin only)",
            description = """
                    The reorder report, lowest stock first. `threshold` defaults to 5.
                    Results are not sortable: the ordering is what makes the page mean
                    something, and re-sorting it would change which rows appear.""")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/low-stock")
    public ResponseEntity<StandardResponse<PageResponse<LowStockResponse>>> lowStock(
            @RequestParam(required = false) Integer threshold,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PageResponse<LowStockResponse> results = productService.findLowStock(threshold, page, size);

        String message = results.totalElements() == 0
                ? "No products are below the threshold"
                : results.totalElements() + " product(s) need restocking";

        return ResponseEntity.ok(StandardResponse.ok(message, results));
    }

    @Operation(summary = "Create a product (Admin only)",
            description = "Also creates the matching inventory row, using `initialStock` or zero.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<StandardResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.created("Product created successfully", created));
    }

    @Operation(summary = "Update a product (Admin only)",
            description = "`initialStock` is ignored here - stock is adjusted through the stock endpoint.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StandardResponse<ProductResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {

        return ResponseEntity.ok(
                StandardResponse.ok("Product updated successfully", productService.update(id, request)));
    }

    @Operation(summary = "Set the stock level for a product (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/stock")
    public ResponseEntity<StandardResponse<Map<String, Object>>> setStock(
            @PathVariable Long id, @Valid @RequestBody StockRequest request) {

        int quantity = inventoryService.setStock(id, request.quantity());
        return ResponseEntity.ok(StandardResponse.ok("Stock updated successfully",
                Map.of("productId", id, "quantity", quantity)));
    }

    @Operation(summary = "Delete a product (Admin only)",
            description = "Rejected with 409 when the product appears in existing orders.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<StandardResponse<Void>> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(StandardResponse.ok("Product deleted successfully", null));
    }
}
