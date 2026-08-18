package rw.smart.ecommerce.core.category.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import rw.smart.ecommerce.utils.pagination.PaginationSupport;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.util.List;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final PaginationSupport pagination;

    public CategoryServiceImpl(CategoryRepository categoryRepository, PaginationSupport pagination) {
        this.categoryRepository = categoryRepository;
        this.pagination = pagination;
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

    /**
     * Deliberately not cached, unlike {@link #findAll()}.
     *
     * The key would have to include the keyword, the page, the size and the sort
     * direction, so every distinct administrator search would take its own entry.
     * A cache that fills with single-use entries evicts the ones that were
     * earning their place — here, the storefront's category list.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> search(String keyword, Integer page, Integer size,
                                                 String sortBy, String direction) {

        Pageable pageable = pagination.forCategories(page, size, sortBy, direction);

        Page<Category> results = keyword == null || keyword.isBlank()
                ? categoryRepository.findAll(pageable)
                : categoryRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);

        return PageResponse.from(results, CategoryResponse::from);
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
