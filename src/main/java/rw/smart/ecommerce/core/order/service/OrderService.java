package rw.smart.ecommerce.core.order.service;

import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.order.dto.OrderRequest;
import rw.smart.ecommerce.core.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    /** Prices the basket, reserves stock and persists the order atomically. */
    OrderResponse placeOrder(OrderRequest request);

    OrderResponse findById(Long id);

    List<OrderResponse> findByUser(Long userId);

    /**
     * Paginated order history, optionally narrowed to one status.
     *
     * {@link #findByUser(Long)} returns the whole history in one list and is kept
     * for the existing endpoint, but it is unbounded by construction — a customer
     * with a thousand orders gets a thousand orders, each with its lines and each
     * line's product. New callers should use this.
     */
    PageResponse<OrderResponse> findByUser(Long userId, OrderStatus status, Integer page, Integer size,
                                           String sortBy, String direction);

    PageResponse<OrderResponse> findAll(OrderStatus status, Integer page, Integer size,
                                        String sortBy, String direction);

    OrderResponse updateStatus(Long id, OrderStatus status);
}
