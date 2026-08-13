package rw.smart.ecommerce.core.product.dao.spec;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.dto.ProductFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the customer catalogue's WHERE clause from whichever criteria the
 * caller supplied.
 *
 * Four optional filters would need sixteen derived query methods to cover every
 * combination; one Criteria predicate list covers them all and still produces a
 * single indexed SQL statement.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
        // utility class, no instances
    }

    public static Specification<Product> matching(ProductFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // The category is needed by every response row; joining it here means
            // the page is built by one statement rather than one per row.
            if (Long.class != query.getResultType()) {
                root.fetch("category", JoinType.INNER);
            }

            if (filter.hasKeyword()) {
                String pattern = "%" + filter.keyword().toLowerCase().trim() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("sku")), pattern)));
            }

            if (filter.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), filter.categoryId()));
            }

            BigDecimal min = filter.minPrice();
            BigDecimal max = filter.maxPrice();
            if (min != null && max != null) {
                predicates.add(builder.between(root.get("price"), min, max));
            } else if (min != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), min));
            } else if (max != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), max));
            }

            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
