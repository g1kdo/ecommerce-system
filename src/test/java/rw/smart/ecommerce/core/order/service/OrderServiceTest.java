package rw.smart.ecommerce.core.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Checkout is the one transactional path in the app, so these tests pin down the
 * whole contract: the total is derived from the lines, stock is decremented inside
 * the same transaction, and any failure rolls the order back completely.
 *
 * DBConnection.getConnection() is a static factory, so it is stubbed with
 * Mockito's static mocking rather than by threading a connection through the API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private OrderDAO orderDAO;
    @Mock
    private InventoryDAO inventoryDAO;
    @Mock
    private Connection connection;

    private OrderService orderService() {
        return new OrderService(orderDAO, inventoryDAO);
    }

    private Order twoLineOrder() {
        Order order = new Order();
        order.setUserId(1);
        order.setItems(List.of(
                item(1, 2, "19.99"),   // 39.98
                item(2, 1, "5.00")));  //  5.00
        return order;
    }

    private OrderItem item(int productId, int quantity, String unitPrice) {
        OrderItem orderItem = new OrderItem();
        orderItem.setProductId(productId);
        orderItem.setQuantity(quantity);
        orderItem.setUnitPrice(new BigDecimal(unitPrice));
        return orderItem;
    }

    @Test
    @DisplayName("a successful order totals its lines, commits once, and decrements each product")
    void placesOrderAndDecrementsStock() throws SQLException {
        try (MockedStatic<DBConnection> dbConnection = mockStatic(DBConnection.class)) {
            dbConnection.when(DBConnection::getConnection).thenReturn(connection);
            when(orderDAO.insertOrderWithItems(eq(connection), any(Order.class))).thenReturn(42);
            when(inventoryDAO.decrementQuantity(eq(connection), anyInt(), anyInt())).thenReturn(true);

            Order order = twoLineOrder();
            assertEquals(42, orderService().placeOrder(order));

            assertEquals(0, new BigDecimal("44.98").compareTo(order.getTotalAmount()),
                    "total should be 39.98 + 5.00 but was " + order.getTotalAmount());
            assertEquals(Status.PENDING, order.getStatus());

            verify(inventoryDAO).decrementQuantity(connection, 1, 2);
            verify(inventoryDAO).decrementQuantity(connection, 2, 1);
            verify(connection).setAutoCommit(false);
            verify(connection).commit();
            verify(connection, never()).rollback();
            verify(connection).setAutoCommit(true);
        }
    }

    @Test
    @DisplayName("insufficient stock rolls the whole order back and never commits")
    void rollsBackWhenStockIsInsufficient() throws SQLException {
        try (MockedStatic<DBConnection> dbConnection = mockStatic(DBConnection.class)) {
            dbConnection.when(DBConnection::getConnection).thenReturn(connection);
            when(orderDAO.insertOrderWithItems(eq(connection), any(Order.class))).thenReturn(42);
            when(inventoryDAO.decrementQuantity(connection, 1, 2)).thenReturn(true);
            when(inventoryDAO.decrementQuantity(connection, 2, 1)).thenReturn(false);

            assertThrows(InsufficientStockException.class, () -> orderService().placeOrder(twoLineOrder()));

            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    @DisplayName("a SQL failure also rolls back, so autocommit cannot commit a half-written order")
    void rollsBackOnSqlFailure() throws SQLException {
        try (MockedStatic<DBConnection> dbConnection = mockStatic(DBConnection.class)) {
            dbConnection.when(DBConnection::getConnection).thenReturn(connection);
            when(orderDAO.insertOrderWithItems(eq(connection), any(Order.class)))
                    .thenThrow(new SQLException("constraint violation"));

            assertThrows(SQLException.class, () -> orderService().placeOrder(twoLineOrder()));

            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    @DisplayName("an empty cart totals zero rather than failing")
    void emptyOrderTotalsZero() throws SQLException {
        try (MockedStatic<DBConnection> dbConnection = mockStatic(DBConnection.class)) {
            dbConnection.when(DBConnection::getConnection).thenReturn(connection);
            when(orderDAO.insertOrderWithItems(eq(connection), any(Order.class))).thenReturn(7);

            Order order = new Order();
            order.setUserId(1);
            order.setItems(List.of());

            assertEquals(7, orderService().placeOrder(order));
            assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()));
            verify(inventoryDAO, never()).decrementQuantity(any(Connection.class), anyInt(), anyInt());
        }
    }

    @Test
    void readsAndUpdatesDelegateToTheDao() throws SQLException {
        Order order = twoLineOrder();
        when(orderDAO.findByUser(1)).thenReturn(List.of(order));
        when(orderDAO.findItemsByOrder(42)).thenReturn(order.getItems());
        when(orderDAO.updateStatus(42, Status.SHIPPED)).thenReturn(true);

        assertEquals(1, orderService().getOrdersForUser(1).size());
        assertEquals(2, orderService().getOrderItems(42).size());
        assertTrue(orderService().updateStatus(42, Status.SHIPPED));
    }
}
