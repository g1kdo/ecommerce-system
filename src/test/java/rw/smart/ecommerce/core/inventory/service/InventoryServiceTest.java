package rw.smart.ecommerce.core.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.inventory.dao.InventoryDAO;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Stock rules: absent rows read as zero, negatives are rejected, shortfalls throw. */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryDAO inventoryDAO;

    private InventoryService inventoryService() {
        return new InventoryService(inventoryDAO);
    }

    private Inventory row(int productId, int quantity) {
        return new Inventory(productId, productId, quantity, LocalDateTime.now());
    }

    @Test
    @DisplayName("a product with no inventory row has zero stock, not an error")
    void missingRowReadsAsZero() throws SQLException {
        when(inventoryDAO.findByProductId(42)).thenReturn(null);

        assertEquals(0, inventoryService().getStock(42));
    }

    @Test
    void readsStockFromTheRow() throws SQLException {
        when(inventoryDAO.findByProductId(1)).thenReturn(row(1, 150));

        assertEquals(150, inventoryService().getStock(1));
    }

    @Test
    @DisplayName("stock for every product is keyed by product id in one query")
    void mapsStockByProductId() throws SQLException {
        when(inventoryDAO.findAll()).thenReturn(List.of(row(1, 150), row(2, 80)));

        Map<Integer, Inventory> byProduct = inventoryService().getStockByProduct();

        assertEquals(2, byProduct.size());
        assertEquals(150, byProduct.get(1).getQuantity());
        assertEquals(80, byProduct.get(2).getQuantity());
        verify(inventoryDAO, times(1)).findAll();
    }

    @Test
    void setStockUpsertsTheQuantity() throws SQLException {
        when(inventoryDAO.upsertQuantity(1, 25)).thenReturn(true);

        assertTrue(inventoryService().setStock(1, 25));
        verify(inventoryDAO).upsertQuantity(1, 25);
    }

    @Test
    @DisplayName("negative stock is rejected before it reaches the database")
    void rejectsNegativeStock() throws SQLException {
        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> inventoryService().setStock(1, -5));

        assertTrue(error.getMessage().toLowerCase().contains("negative"));
        verify(inventoryDAO, never()).upsertQuantity(anyInt(), anyInt());
    }

    @Test
    void rejectsNonPositiveReduction() throws SQLException {
        assertThrows(InvalidInputException.class, () -> inventoryService().reduceStock(1, 0));
        verify(inventoryDAO, never()).decrementQuantity(anyInt(), anyInt());
    }

    @Test
    @DisplayName("a decrement the database refuses becomes InsufficientStockException")
    void refusedDecrementThrows() throws SQLException {
        when(inventoryDAO.decrementQuantity(1, 10)).thenReturn(false);

        assertThrows(InsufficientStockException.class, () -> inventoryService().reduceStock(1, 10));
    }

    @Test
    void successfulReductionDoesNotThrow() throws SQLException {
        when(inventoryDAO.decrementQuantity(1, 10)).thenReturn(true);

        assertDoesNotThrow(() -> inventoryService().reduceStock(1, 10));
    }
}
