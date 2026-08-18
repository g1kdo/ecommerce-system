package rw.smart.ecommerce.core.category.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Category listing (public) and management (admin)")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "List all categories",
            description = "Served from a 30-minute cache; categories change rarely and are read constantly.")
    @GetMapping
    public ResponseEntity<StandardResponse<List<CategoryResponse>>> findAll() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(StandardResponse.ok(categories.size() + " category/categories retrieved", categories));
    }

    @Operation(summary = "Get a single category by id")
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<CategoryResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(StandardResponse.ok("Category retrieved successfully", categoryService.findById(id)));
    }

    @Operation(summary = "Search categories with pagination (Admin only)",
            description = """
                    `keyword` matches the category name. Sortable by id or name. This is the
                    management view; the public listing above returns every category from a
                    single cached call and is the one a storefront should use.""")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<StandardResponse<PageResponse<CategoryResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {

        PageResponse<CategoryResponse> results = categoryService.search(keyword, page, size, sortBy, direction);
        return ResponseEntity.ok(StandardResponse.ok("Categories retrieved successfully", results));
    }

    @Operation(summary = "Create a category (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<StandardResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.created("Category created successfully", created));
    }

    @Operation(summary = "Update a category (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StandardResponse<CategoryResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {

        return ResponseEntity.ok(
                StandardResponse.ok("Category updated successfully", categoryService.update(id, request)));
    }

    @Operation(summary = "Delete a category (Admin only)",
            description = "Rejected with 409 while any product still references it.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<StandardResponse<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(StandardResponse.ok("Category deleted successfully", null));
    }
}
