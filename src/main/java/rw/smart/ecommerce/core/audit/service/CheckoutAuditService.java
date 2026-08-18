package rw.smart.ecommerce.core.audit.service;

public interface CheckoutAuditService {

    /**
     * Records that a checkout was refused for want of stock.
     *
     * Called from inside the order transaction, immediately before that
     * transaction is rolled back — so the implementation has to commit
     * independently of its caller.
     */
    void recordShortfall(Long userId, Long productId, int requested, int available);
}
