package rw.smart.ecommerce.core.product.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Product {

    private int productId;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku; // Stock Keeping Unit
    private int categoryId;
    private LocalDateTime createdAt;

    public Product() {

    }

    public Product(int productId, String name, String description, BigDecimal price, String sku, int categoryId, LocalDateTime createdAt) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.sku = sku;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name + " (" + sku + ") - $" + price;
    }
}
