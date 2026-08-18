package rw.smart.ecommerce.core.order.dao.projection;

/** A product that appears in the same orders as the one being viewed. */
public interface RelatedProduct {

    Long getProductId();

    String getProductName();

    long getTimesBoughtTogether();
}
