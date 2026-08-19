package rw.smart.ecommerce.core.category.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.util.List;

/** GraphQL entry points for categories. */
@Controller
public class CategoryGraphQLController {

    private final CategoryService categoryService;

    public CategoryGraphQLController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @QueryMapping
    public List<CategoryResponse> categories() {
        return categoryService.findAll();
    }

    @QueryMapping
    public CategoryResponse category(@Argument Long id) {
        return categoryService.findById(id);
    }

    /**
     * Management view. The public {@code categories} query above stays as it is:
     * it serves storefront navigation from one cached call, and paging it would
     * defeat that.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public PageResponse<CategoryResponse> searchCategories(@Argument String keyword,
                                                           @Argument Integer page,
                                                           @Argument Integer size,
                                                           @Argument String sortBy,
                                                           @Argument String direction) {

        return categoryService.search(keyword, page, size, sortBy, direction);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public CategoryResponse createCategory(@Argument @Valid CategoryRequest input) {
        return categoryService.create(input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public CategoryResponse updateCategory(@Argument Long id, @Argument @Valid CategoryRequest input) {
        return categoryService.update(id, input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public boolean deleteCategory(@Argument Long id) {
        categoryService.delete(id);
        return true;
    }
}
