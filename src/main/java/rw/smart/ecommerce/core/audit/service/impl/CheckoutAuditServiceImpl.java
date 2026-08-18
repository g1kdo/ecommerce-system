package rw.smart.ecommerce.core.audit.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.core.audit.dao.CheckoutShortfallRepository;
import rw.smart.ecommerce.core.audit.model.CheckoutShortfall;
import rw.smart.ecommerce.core.audit.service.CheckoutAuditService;

/**
 * The one place in this codebase where {@code REQUIRES_NEW} is the right answer.
 *
 * A stock shortfall is recorded from inside {@code placeOrder}, one statement
 * before that method throws and its transaction rolls back. Under the default
 * {@code REQUIRED} the insert would join the caller's transaction and be undone
 * along with everything else — the record of the failure destroyed by the
 * failure it records. {@code REQUIRES_NEW} suspends the caller's transaction,
 * commits this row on a connection of its own, then resumes it; the rollback
 * that follows cannot reach what has already committed.
 *
 * Two consequences are worth stating outright.
 *
 * The suspended transaction is still holding row locks on the inventory it has
 * already decremented, and this one runs on a second connection while it waits.
 * That is safe only because the two touch disjoint tables. A {@code REQUIRES_NEW}
 * block that wrote to {@code inventory} would deadlock against its own caller,
 * and no amount of retrying would resolve it.
 *
 * Nothing is caught here. A failure inside a {@code REQUIRES_NEW} method cannot
 * usefully be swallowed inside it: the transaction is already marked
 * rollback-only by the time the catch block runs, so the commit at method exit
 * would throw {@code UnexpectedRollbackException} regardless. The caller catches
 * instead — see {@code OrderServiceImpl.placeOrder} — which is also the only
 * place that knows the audit is optional and the stock error is not.
 */
@Slf4j
@Service
public class CheckoutAuditServiceImpl implements CheckoutAuditService {

    private final CheckoutShortfallRepository shortfallRepository;

    public CheckoutAuditServiceImpl(CheckoutShortfallRepository shortfallRepository) {
        this.shortfallRepository = shortfallRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordShortfall(Long userId, Long productId, int requested, int available) {
        CheckoutShortfall shortfall = new CheckoutShortfall();
        shortfall.setUserId(userId);
        shortfall.setProductId(productId);
        shortfall.setRequestedQuantity(requested);
        shortfall.setAvailableQuantity(available);

        shortfallRepository.save(shortfall);

        log.info("Checkout shortfall: user {} wanted {} of product {}, {} on hand",
                userId, requested, productId, available);
    }
}
