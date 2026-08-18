package rw.smart.ecommerce.core.product.dao.projection;

/** A product at or below the reorder threshold. */
public interface LowStockProduct {

    Long getProductId();

    String getSku();

    String getName();

    String getCategoryName();

    int getQuantity();
}
