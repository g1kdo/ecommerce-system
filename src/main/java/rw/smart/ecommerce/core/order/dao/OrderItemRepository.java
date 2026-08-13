package rw.smart.ecommerce.core.order.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.order.model.item.OrderItem;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Blocks product deletion while historical orders still reference it. */
    boolean existsByProductId(Long productId);
}
