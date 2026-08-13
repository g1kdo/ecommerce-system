package rw.smart.ecommerce.core.product.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.utils.pagination.PaginationSupport;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.product.dto.ProductFilter;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.order.dao.OrderItemRepository;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.review.dao.ReviewRepository;
import rw.smart.ecommerce.core.product.dao.spec.ProductSpecifications;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final PaginationSupport pagination;

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              InventoryRepository inventoryRepository,
                              OrderItemRepository orderItemRepository,
                              ReviewRepository reviewRepository,
                              PaginationSupport pagination) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
        this.pagination = pagination;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku()))
            throw new DuplicateResourceException("SKU already in use: " + request.sku());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setSku(request.sku());
        product.setCategory(category);

        Product saved = productRepository.save(product);

        // Every product gets an inventory row up front so stock lookups never have
        // to special-case a missing row later.
        int initialStock = request.initialStock() == null ? 0 : request.initialStock();
        Inventory inventory = new Inventory();
        inventory.setProduct(saved);
        inventory.setQuantity(initialStock);
        inventoryRepository.save(inventory);

        log.debug("Created product {} (sku {}) with stock {}", saved.getId(), saved.getSku(), initialStock);
        return ProductResponse.from(saved, initialStock);
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));

        if (!product.getSku().equalsIgnoreCase(request.sku())
                && productRepository.existsBySkuIgnoreCase(request.sku()))
            throw new DuplicateResourceException("SKU already in use: " + request.sku());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setSku(request.sku());
        product.setCategory(category);

        Product saved = productRepository.save(product);
        // initialStock is intentionally ignored on update — stock is adjusted
        // through the inventory endpoint so the two paths cannot disagree.
        return ProductResponse.from(saved, currentStock(id));
    }

    @Override
    @Cacheable(value = CacheConfig.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));

        return ProductResponse.from(product, currentStock(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> browse(ProductFilter filter, Integer page, Integer size,
                                                String sortBy, String direction) {

        ProductFilter effective = filter == null ? ProductFilter.empty() : filter;
        Pageable pageable = pagination.forProducts(page, size, sortBy, direction);

        Page<Product> products = productRepository.findAll(ProductSpecifications.matching(effective), pageable);

        // Stock for the whole page in one query rather than one per row.
        Map<Long, Integer> stockByProduct = stockFor(products.getContent());

        return PageResponse.from(products,
                product -> ProductResponse.from(product, stockByProduct.getOrDefault(product.getId(), 0)));
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS, key = "#id")
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));

        // Order items snapshot the price but still reference the product row, and
        // the FK is RESTRICT — deleting would erase a customer's order history.
        if (orderItemRepository.existsByProductId(id))
            throw new DuplicateResourceException(
                    "Cannot delete product " + id + " because it appears in existing orders.");

        inventoryRepository.deleteByProductId(id);
        productRepository.delete(product);

        // Documents have no cascading foreign key, so the reviews are cleaned up
        // explicitly. A document-store failure here must not roll back the
        // relational delete that already succeeded.
        try {
            long removed = reviewRepository.deleteByProductId(id);
            log.debug("Removed {} review document(s) for deleted product {}", removed, id);
        } catch (RuntimeException e) {
            log.warn("Product {} was deleted but its review documents could not be removed: {}", id, e.getMessage());
        }
    }

    private int currentStock(Long productId) {
        return productRepository.findStockQuantity(productId).orElse(0);
    }

    /**
     * Stock for a whole page of products, keyed by product id. Hibernate resolves
     * {@code getProduct().getId()} from the foreign key without initializing the
     * lazy proxy, so this stays a single query.
     */
    private Map<Long, Integer> stockFor(List<Product> products) {
        if (products.isEmpty()) return Map.of();

        List<Long> ids = products.stream().map(Product::getId).toList();
        return inventoryRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(
                        inventory -> inventory.getProduct().getId(),
                        Inventory::getQuantity,
                        (first, second) -> first));
    }
}
