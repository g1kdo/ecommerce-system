package rw.smart.ecommerce.core.category.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.List;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @CacheEvict(value = CacheConfig.CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name()))
            throw new DuplicateResourceException("Category already exists: " + request.name());

        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    @CacheEvict(value = CacheConfig.CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));

        if (!category.getName().equalsIgnoreCase(request.name())
                && categoryRepository.existsByNameIgnoreCase(request.name()))
            throw new DuplicateResourceException("Category already exists: " + request.name());

        category.setName(request.name());
        category.setDescription(request.description());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    @Cacheable(value = CacheConfig.CATEGORIES, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    @Override
    @Cacheable(value = CacheConfig.CATEGORIES, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Override
    @CacheEvict(value = CacheConfig.CATEGORIES, allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));

        long productCount = categoryRepository.countProductsInCategory(id);
        if (productCount > 0)
            throw new DuplicateResourceException(
                    "Cannot delete category '" + category.getName() + "': " + productCount + " product(s) still use it.");

        categoryRepository.delete(category);
        log.debug("Deleted category {}", id);
    }
}
