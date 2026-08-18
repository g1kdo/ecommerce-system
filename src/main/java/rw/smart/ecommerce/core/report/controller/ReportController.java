package rw.smart.ecommerce.core.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategoryRevenueResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategorySummaryResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CustomerSpendResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.DailySalesResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.MissedDemandResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.OrderStatusResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.StockShareResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.TopProductResponse;
import rw.smart.ecommerce.core.report.service.ReportService;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Administrative reporting.
 *
 * The role check is declared once on the class rather than repeated per method.
 * Every endpoint here aggregates across all customers — revenue, who spends the
 * most, what the catalogue failed to sell — and repeating the annotation is what
 * eventually leaves one of them public.
 *
 * Windows are optional throughout. Omitting both dates means the last 30 days;
 * the service resolves and bounds them.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reports", description = "Sales, catalogue and customer reporting (admin only)")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Sales per day",
            description = "Order count, revenue and average order value per day. Cancelled orders excluded.")
    @GetMapping("/sales/daily")
    public ResponseEntity<StandardResponse<List<DailySalesResponse>>> dailySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<DailySalesResponse> report = reportService.dailySales(from, to);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " day(s) of sales", report));
    }

    @Operation(summary = "Total revenue over a window")
    @GetMapping("/sales/revenue")
    public ResponseEntity<StandardResponse<Map<String, Object>>> totalRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        BigDecimal revenue = reportService.totalRevenue(from, to);
        return ResponseEntity.ok(StandardResponse.ok("Revenue calculated", Map.of("revenue", revenue)));
    }

    @Operation(summary = "Order counts and value by status")
    @GetMapping("/orders/by-status")
    public ResponseEntity<StandardResponse<List<OrderStatusResponse>>> ordersByStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<OrderStatusResponse> report = reportService.ordersByStatus(from, to);
        return ResponseEntity.ok(StandardResponse.ok("Order status breakdown retrieved", report));
    }

    @Operation(summary = "Best-selling products",
            description = "Ranked by units sold. Revenue is summed from each line's own snapshot price, "
                    + "so a later markdown does not rewrite what the product earned.")
    @GetMapping("/products/top-selling")
    public ResponseEntity<StandardResponse<List<TopProductResponse>>> topSelling(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {

        List<TopProductResponse> report = reportService.topSellingProducts(from, to, limit);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " product(s) ranked", report));
    }

    @Operation(summary = "Demand refused for want of stock",
            description = """
                    Aggregated from the checkout-shortfall audit. These are sales the catalogue
                    could not make, and they are invisible in the orders table because the order
                    was never written.""")
    @GetMapping("/products/missed-demand")
    public ResponseEntity<StandardResponse<List<MissedDemandResponse>>> missedDemand(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) Integer limit) {

        List<MissedDemandResponse> report = reportService.missedDemand(since, limit);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " product(s) ran short", report));
    }

    @Operation(summary = "Revenue per category")
    @GetMapping("/categories/revenue")
    public ResponseEntity<StandardResponse<List<CategoryRevenueResponse>>> revenueByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<CategoryRevenueResponse> report = reportService.revenueByCategory(from, to);
        return ResponseEntity.ok(StandardResponse.ok("Category revenue retrieved", report));
    }

    @Operation(summary = "Catalogue shape per category",
            description = "Product count and price range. Prices are null for a category with no products.")
    @GetMapping("/categories/summary")
    public ResponseEntity<StandardResponse<List<CategorySummaryResponse>>> categorySummary() {
        List<CategorySummaryResponse> report = reportService.categorySummary();
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " category/categories summarised", report));
    }

    @Operation(summary = "How a category's stock is distributed across its products")
    @GetMapping("/categories/{categoryId}/stock-distribution")
    public ResponseEntity<StandardResponse<List<StockShareResponse>>> stockDistribution(
            @PathVariable Long categoryId) {

        List<StockShareResponse> report = reportService.stockDistribution(categoryId);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " product(s) in this category", report));
    }

    @Operation(summary = "Highest-spending customers")
    @GetMapping("/customers/top")
    public ResponseEntity<StandardResponse<List<CustomerSpendResponse>>> topCustomers(
            @RequestParam(required = false) Integer limit) {

        List<CustomerSpendResponse> report = reportService.topCustomers(limit);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " customer(s) ranked", report));
    }

    @Operation(summary = "Customers who have not ordered since a date",
            description = "Ranked by lifetime spend, so the most valuable lapsed customers come first. "
                    + "`since` defaults to 90 days ago.")
    @GetMapping("/customers/lapsed")
    public ResponseEntity<StandardResponse<List<CustomerSpendResponse>>> lapsedCustomers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) Integer limit) {

        List<CustomerSpendResponse> report = reportService.lapsedCustomers(since, limit);
        return ResponseEntity.ok(StandardResponse.ok(report.size() + " lapsed customer(s)", report));
    }
}
