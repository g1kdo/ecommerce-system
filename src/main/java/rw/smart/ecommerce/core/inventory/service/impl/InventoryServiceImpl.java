package rw.smart.ecommerce.core.inventory.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

@Slf4j
@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository, ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public int getStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS, key = "#productId")
    @Transactional
    public int setStock(Long productId, int quantity) {
        if (quantity < 0) throw new InvalidInputException("Stock quantity cannot be negative.");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));

        // Upsert: products created outside this application may have no row yet.
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setProduct(product);
                    return created;
                });

        inventory.setQuantity(quantity);
        inventoryRepository.save(inventory);

        log.debug("Stock for product {} set to {}", productId, quantity);
        return quantity;
    }

    @Override
    @CacheEvict(value = CacheConfig.PRODUCTS, key = "#productId")
    @Transactional
    public void reduceStock(Long productId, int amount) {
        if (amount <= 0) throw new InvalidInputException("Amount to remove must be greater than zero.");

        // The conditional UPDATE checks and decrements in one statement, so no
        // concurrent caller can slip between the read and the write.
        int updated = inventoryRepository.decrementQuantity(productId, amount);
        if (updated == 0)
            throw new InsufficientStockException("Insufficient stock for product " + productId);
    }
}
