package rw.smart.ecommerce.core.inventory.dao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.inventory.model.Inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Row lock taken while an order is being placed so two concurrent checkouts
     * cannot both read the same quantity and both decide there is enough stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(Long productId);

    /** One query for a whole page of products instead of one query per row. */
    @Query("SELECT i FROM Inventory i WHERE i.product.id IN :productIds")
    List<Inventory> findByProductIdIn(Collection<Long> productIds);

    /**
     * Conditional decrement: the {@code >=} guard makes "check stock" and
     * "reduce stock" a single atomic statement. A return of 0 means the stock was
     * insufficient, with no read-then-write race in between.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :amount, i.lastUpdated = CURRENT_TIMESTAMP "
            + "WHERE i.product.id = :productId AND i.quantity >= :amount")
    int decrementQuantity(Long productId, int amount);

    /** Returns stock to the shelf when an order is cancelled. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :amount, i.lastUpdated = CURRENT_TIMESTAMP "
            + "WHERE i.product.id = :productId")
    int incrementQuantity(Long productId, int amount);

    void deleteByProductId(Long productId);
}
