package rw.smart.ecommerce.core.report.dto;

import rw.smart.ecommerce.core.audit.dao.projection.MissedDemand;
import rw.smart.ecommerce.core.category.dao.projection.CategoryRevenue;
import rw.smart.ecommerce.core.category.dao.projection.CategorySummary;
import rw.smart.ecommerce.core.order.dao.projection.DailySales;
import rw.smart.ecommerce.core.order.dao.projection.OrderStatusSummary;
import rw.smart.ecommerce.core.order.dao.projection.RelatedProduct;
import rw.smart.ecommerce.core.order.dao.projection.TopSellingProduct;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.product.dao.projection.StockShare;
import rw.smart.ecommerce.core.user.dao.projection.CustomerSpend;

import java.math.BigDecimal;

/**
 * The report responses, collected in one file because each is a handful of
 * lines and they are only ever read together.
 *
 * Every one of them maps a repository projection to a record. The projections
 * are proxies over a JPA {@code Tuple}: serializing one directly would publish
 * the query's column aliases as the API's field names and make any change to the
 * SQL a breaking change to the contract.
 *
 * Nulls are resolved here rather than in the queries. A category with no
 * products genuinely has no average price, and {@code COALESCE(AVG(...), 0)} in
 * the SQL would claim it sells something free; the report renders it as zero
 * because that is a rendering decision, and it is made where rendering happens.
 */
public final class ReportDtos {

    private ReportDtos() {
        // container for the report records only
    }

    public record DailySalesResponse(
            String day,
            long orderCount,
            BigDecimal revenue,
            BigDecimal averageOrderValue) {

        public static DailySalesResponse from(DailySales row) {
            return new DailySalesResponse(
                    row.getDay(),
                    row.getOrderCount(),
                    zeroIfNull(row.getRevenue()),
                    zeroIfNull(row.getAverageOrderValue()));
        }
    }

    public record OrderStatusResponse(
            OrderStatus status,
            long orderCount,
            BigDecimal revenue) {

        public static OrderStatusResponse from(OrderStatusSummary row) {
            return new OrderStatusResponse(row.getStatus(), row.getOrderCount(), zeroIfNull(row.getRevenue()));
        }
    }

    public record TopProductResponse(
            Long productId,
            String productName,
            String sku,
            long unitsSold,
            BigDecimal revenue) {

        public static TopProductResponse from(TopSellingProduct row) {
            return new TopProductResponse(
                    row.getProductId(),
                    row.getProductName(),
                    row.getSku(),
                    row.getUnitsSold(),
                    zeroIfNull(row.getRevenue()));
        }
    }

    public record CategoryRevenueResponse(
            Long categoryId,
            String name,
            long orderCount,
            long unitsSold,
            BigDecimal revenue) {

        public static CategoryRevenueResponse from(CategoryRevenue row) {
            return new CategoryRevenueResponse(
                    row.getCategoryId(),
                    row.getName(),
                    row.getOrderCount(),
                    row.getUnitsSold(),
                    zeroIfNull(row.getRevenue()));
        }
    }

    public record CategorySummaryResponse(
            Long categoryId,
            String name,
            long productCount,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal averagePrice) {

        public static CategorySummaryResponse from(CategorySummary row) {
            // AVG comes back as a Double from HQL; the API speaks in money, and
            // money is scaled decimal.
            BigDecimal average = row.getAveragePrice() == null
                    ? null
                    : BigDecimal.valueOf(row.getAveragePrice()).setScale(2, java.math.RoundingMode.HALF_UP);

            return new CategorySummaryResponse(
                    row.getCategoryId(),
                    row.getName(),
                    row.getProductCount(),
                    row.getMinPrice(),
                    row.getMaxPrice(),
                    average);
        }
    }

    public record CustomerSpendResponse(
            Long userId,
            String fullName,
            String email,
            long orderCount,
            BigDecimal totalSpent) {

        public static CustomerSpendResponse from(CustomerSpend row) {
            return new CustomerSpendResponse(
                    row.getUserId(),
                    row.getFullName(),
                    row.getEmail(),
                    row.getOrderCount(),
                    zeroIfNull(row.getTotalSpent()));
        }
    }

    public record StockShareResponse(
            Long productId,
            String sku,
            String name,
            int quantity,
            BigDecimal shareOfCategoryStock) {

        public static StockShareResponse from(StockShare row) {
            return new StockShareResponse(
                    row.getProductId(),
                    row.getSku(),
                    row.getName(),
                    row.getQuantity(),
                    zeroIfNull(row.getShareOfCategoryStock()));
        }
    }

    /**
     * Demand that was refused for want of stock. The product id is reported
     * without a name: the audit rows carry no foreign key, so the product may
     * since have been deleted, and inventing a name for one that is gone would be
     * worse than reporting the id.
     */
    public record MissedDemandResponse(
            Long productId,
            long occurrences,
            long unitsMissed) {

        public static MissedDemandResponse from(MissedDemand row) {
            return new MissedDemandResponse(row.getProductId(), row.getOccurrences(), row.getUnitsMissed());
        }
    }

    public record RelatedProductResponse(
            Long productId,
            String productName,
            long timesBoughtTogether) {

        public static RelatedProductResponse from(RelatedProduct row) {
            return new RelatedProductResponse(
                    row.getProductId(), row.getProductName(), row.getTimesBoughtTogether());
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
