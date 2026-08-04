package rw.smart.ecommerce.core.order.model.item;

import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;

/**
 * A line in the shopping cart before checkout. Unlike {@link OrderItem} it keeps
 * the whole Product (so the cart can show names without re-querying) and is not
 * persisted — it is converted to an OrderItem when the order is placed.
 */
public class CartItem {

    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getProductId() {
        return product.getProductId();
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public BigDecimal getUnitPrice() {
        return product.getPrice();
    }

    public BigDecimal getLineTotal() {
        return getUnitPrice().multiply(BigDecimal.valueOf(quantity));
    }

    /** Snapshots the current price onto a persistable order line. */
    public OrderItem toOrderItem() {
        OrderItem item = new OrderItem();
        item.setProductId(getProductId());
        item.setQuantity(quantity);
        item.setUnitPrice(getUnitPrice());
        return item;
    }
}
