package rw.smart.ecommerce.core.report.service;

import rw.smart.ecommerce.core.report.dto.ReportDtos.CategoryRevenueResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategorySummaryResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CustomerSpendResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.DailySalesResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.MissedDemandResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.OrderStatusResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.StockShareResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.TopProductResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate reporting over the relational store.
 *
 * Every window is a half-open interval — {@code from} inclusive, {@code to}
 * inclusive at day granularity because the service adds a day to it before
 * querying. That conversion happens once, here, so a report cannot silently drop
 * the orders placed on its own end date.
 */
public interface ReportService {

    /** Revenue, order count and average order value per day. */
    List<DailySalesResponse> dailySales(LocalDate from, LocalDate to);

    /** Order counts and value per status. */
    List<OrderStatusResponse> ordersByStatus(LocalDate from, LocalDate to);

    /** Total revenue over the window, cancelled orders excluded. */
    BigDecimal totalRevenue(LocalDate from, LocalDate to);

    List<TopProductResponse> topSellingProducts(LocalDate from, LocalDate to, Integer limit);

    List<CategoryRevenueResponse> revenueByCategory(LocalDate from, LocalDate to);

    /** Catalogue shape: product count and price range per category. */
    List<CategorySummaryResponse> categorySummary();

    /** How a category's stock is spread across its products. */
    List<StockShareResponse> stockDistribution(Long categoryId);

    List<CustomerSpendResponse> topCustomers(Integer limit);

    /** Customers who have bought before but not since {@code since}. */
    List<CustomerSpendResponse> lapsedCustomers(LocalDate since, Integer limit);

    /** Checkouts refused for want of stock, aggregated per product. */
    List<MissedDemandResponse> missedDemand(LocalDate since, Integer limit);
}
