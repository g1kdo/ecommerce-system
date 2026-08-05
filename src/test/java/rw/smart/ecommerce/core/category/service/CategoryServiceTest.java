package rw.smart.ecommerce.core.category.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.category.dao.CategoryDAO;
import rw.smart.ecommerce.core.category.model.Category;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CategoryService is a thin pass-through; these tests hold that contract in place. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryDAO categoryDAO;

    private CategoryService categoryService() {
        return new CategoryService(categoryDAO);
    }

    @Test
    void listsCategories() throws SQLException {
        when(categoryDAO.findAll()).thenReturn(List.of(
                new Category(1, "Electronics", "Devices and gadgets"),
                new Category(2, "Books", null)));

        assertEquals(2, categoryService().getAllCategories().size());
    }

    @Test
    void findsOneCategory() throws SQLException {
        Category category = new Category(1, "Electronics", "Devices and gadgets");
        when(categoryDAO.findById(1)).thenReturn(category);

        assertSame(category, categoryService().getCategory(1));
    }

    @Test
    void createsUpdatesAndDeletes() throws SQLException {
        Category category = new Category(0, "Peripherals", null);
        when(categoryDAO.insert(category)).thenReturn(9);
        when(categoryDAO.update(category)).thenReturn(true);
        when(categoryDAO.delete(9)).thenReturn(true);

        assertEquals(9, categoryService().createCategory(category));
        assertTrue(categoryService().updateCategory(category));
        assertTrue(categoryService().deleteCategory(9));

        verify(categoryDAO).insert(category);
        verify(categoryDAO).update(category);
        verify(categoryDAO).delete(9);
    }
}
