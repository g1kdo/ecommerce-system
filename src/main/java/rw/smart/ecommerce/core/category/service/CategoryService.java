package rw.smart.ecommerce.core.category.service;

import rw.smart.ecommerce.core.category.dao.CategoryDAO;
import rw.smart.ecommerce.core.category.model.Category;

import java.sql.SQLException;
import java.util.List;

public class CategoryService {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> getAllCategories() throws SQLException {
        return categoryDAO.findAll();
    }

    public Category getCategory(int categoryId) throws SQLException {
        return categoryDAO.findById(categoryId);
    }

    public int createCategory(Category category) throws SQLException {
        return categoryDAO.insert(category);
    }

    public boolean updateCategory(Category category) throws SQLException {
        return categoryDAO.update(category);
    }

    public boolean deleteCategory(int categoryId) throws SQLException {
        return categoryDAO.delete(categoryId);
    }
}
