package rw.smart.ecommerce.core.order.service;

import rw.smart.ecommerce.core.inventory.dao.InventoryDAO;
import rw.smart.ecommerce.core.order.dao.OrderDAO;
import rw.smart.ecommerce.core.order.enums.Status;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.utils.DBConnection;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class OrderService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    /**
     * Places an order: computes the total (denormalized on Orders.total_amount),
     * persists the order + items transactionally, then decrements stock per item.
     */
    public int placeOrder(Order order) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            total = total.add(item.getLineTotal());
        }

        order.setTotalAmount(total);
        order.setStatus(Status.PENDING);

        try(Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId = orderDAO.insertOrderWithItems(conn, order);

                for (OrderItem item : order.getItems()) {
                    boolean ok = inventoryDAO.decrementQuantity(conn, item.getProductId(), item.getQuantity());
                    if (!ok) throw new InsufficientStockException("Insufficient stock for product_id= " + item.getProductId());
                }

                conn.commit();
                return orderId;
            } catch (InsufficientStockException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Order> getOrdersForUser(int userId) throws SQLException {
        return orderDAO.findByUser(userId);
    }

    public List<OrderItem> getOrderItems(int orderId) throws SQLException {
        return orderDAO.findItemsByOrder(orderId);
    }

    public boolean updateStatus(int orderId, Status status) throws SQLException {
        return orderDAO.updateStatus(orderId, status);
    }
}
