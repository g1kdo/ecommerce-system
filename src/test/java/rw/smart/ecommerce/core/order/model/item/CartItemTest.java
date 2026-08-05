package rw.smart.ecommerce.core.order.model.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Cart arithmetic and the price snapshot taken when a line becomes an order item. */
class CartItemTest {

    private Product product(String price) {
        Product product = new Product();
        product.setProductId(1);
        product.setName("Wireless Mouse");
        product.setPrice(new BigDecimal(price));
        return product;
    }

    @Test
    void lineTotalMultipliesPriceByQuantity() {
        CartItem line = new CartItem(product("12.50"), 3);

        assertEquals(0, new BigDecimal("37.50").compareTo(line.getLineTotal()));
    }

    @Test
    void addQuantityAccumulates() {
        CartItem line = new CartItem(product("10.00"), 2);
        line.addQuantity(3);

        assertEquals(5, line.getQuantity());
        assertEquals(0, new BigDecimal("50.00").compareTo(line.getLineTotal()));
    }

    @Test
    @DisplayName("converting to an order item snapshots the current price")
    void toOrderItemSnapshotsPrice() {
        Product product = product("19.99");
        CartItem line = new CartItem(product, 2);

        OrderItem item = line.toOrderItem();
        product.setPrice(new BigDecimal("29.99")); // a later price change must not apply

        assertEquals(1, item.getProductId());
        assertEquals(2, item.getQuantity());
        assertEquals(0, new BigDecimal("19.99").compareTo(item.getUnitPrice()));
        assertEquals(0, new BigDecimal("39.98").compareTo(item.getLineTotal()));
    }

    @Test
    void exposesTheUnderlyingProduct() {
        Product product = product("5.00");
        CartItem line = new CartItem(product, 1);

        assertSame(product, line.getProduct());
        assertEquals(1, line.getProductId());
        assertEquals(0, new BigDecimal("5.00").compareTo(line.getUnitPrice()));
    }
}
