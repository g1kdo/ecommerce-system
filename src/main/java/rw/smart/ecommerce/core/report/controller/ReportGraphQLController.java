package rw.smart.ecommerce.core.report.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategoryRevenueResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategorySummaryResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CustomerSpendResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.DailySalesResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.MissedDemandResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.OrderStatusResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.StockShareResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.TopProductResponse;
import rw.smart.ecommerce.core.product.dto.LowStockResponse;
import rw.smart.ecommerce.core.report.dto.ReportRoots.CatalogueReport;
import rw.smart.ecommerce.core.report.dto.ReportRoots.SalesReport;
import rw.smart.ecommerce.core.report.service.ReportService;
import rw.smart.ecommerce.utils.GraphQlDates;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GraphQL reporting.
 *
 * This is the part of Phase 3 where GraphQL earns its place rather than merely
 * matching REST. Section 6.3 of the performance report measured the one case
 * GraphQL wins outright — a screen needing several related resources at once —
 * and an administrator's dashboard is exactly that shape: revenue, a daily
 * chart, a status breakdown and a top-products table are four REST calls and one
 * GraphQL query.
 *
 * The saving is not only round trips. Each panel is a {@code @SchemaMapping}
 * field resolver, so a dashboard that renders only the revenue headline never
 * runs the {@code date_trunc} group-by behind {@code daily}. Over REST that
 * choice does not exist: {@code /reports/sales/daily} computes the daily series
 * whether the caller draws it or not.
 *
 * <h4>Authorisation</h4>
 *
 * The two root queries are {@code ADMIN}. The field resolvers below are not
 * annotated, and that is deliberate rather than an oversight of the kind
 * section 6.6 records: a {@code @SchemaMapping} is only invoked with a source
 * object of its parent type, no other query returns {@code SalesReport} or
 * {@code CatalogueReport}, and therefore the only way to reach any of these
 * fields is through a root that has already refused a non-administrator.
 */
@Controller
public class ReportGraphQLController {

    private final ReportService reportService;
    private final ProductService productService;

    public ReportGraphQLController(ReportService reportService, ProductService productService) {
        this.reportService = reportService;
        this.productService = productService;
    }

    // ---------------- Roots ----------------

    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public SalesReport salesReport(@Argument String from, @Argument String to) {
        return new SalesReport(GraphQlDates.parse(from, "from"), GraphQlDates.parse(to, "to"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public CatalogueReport catalogueReport() {
        return new CatalogueReport();
    }

    // ---------------- Sales report fields ----------------

    @SchemaMapping(typeName = "SalesReport")
    public BigDecimal totalRevenue(SalesReport report) {
        return reportService.totalRevenue(report.from(), report.to());
    }

    @SchemaMapping(typeName = "SalesReport")
    public List<DailySalesResponse> daily(SalesReport report) {
        return reportService.dailySales(report.from(), report.to());
    }

    @SchemaMapping(typeName = "SalesReport")
    public List<OrderStatusResponse> byStatus(SalesReport report) {
        return reportService.ordersByStatus(report.from(), report.to());
    }

    @SchemaMapping(typeName = "SalesReport")
    public List<TopProductResponse> topProducts(SalesReport report, @Argument Integer limit) {
        return reportService.topSellingProducts(report.from(), report.to(), limit);
    }

    @SchemaMapping(typeName = "SalesReport")
    public List<CategoryRevenueResponse> revenueByCategory(SalesReport report) {
        return reportService.revenueByCategory(report.from(), report.to());
    }

    @SchemaMapping(typeName = "SalesReport")
    public List<CustomerSpendResponse> topCustomers(SalesReport report, @Argument Integer limit) {
        return reportService.topCustomers(limit);
    }

    /**
     * {@code since} is its own argument rather than the report's window: "who has
     * not bought since March" is a different question from "what did we sell in
     * March", and sharing the window would silently answer the wrong one.
     */
    @SchemaMapping(typeName = "SalesReport")
    public List<CustomerSpendResponse> lapsedCustomers(SalesReport report,
                                                       @Argument String since,
                                                       @Argument Integer limit) {

        return reportService.lapsedCustomers(GraphQlDates.parse(since, "since"), limit);
    }

    // ---------------- Catalogue report fields ----------------

    @SchemaMapping(typeName = "CatalogueReport")
    public List<CategorySummaryResponse> categories(CatalogueReport report) {
        return reportService.categorySummary();
    }

    @SchemaMapping(typeName = "CatalogueReport")
    public PageResponse<LowStockResponse> lowStock(CatalogueReport report,
                                                   @Argument Integer threshold,
                                                   @Argument Integer page,
                                                   @Argument Integer size) {

        return productService.findLowStock(threshold, page, size);
    }

    @SchemaMapping(typeName = "CatalogueReport")
    public List<StockShareResponse> stockDistribution(CatalogueReport report, @Argument Long categoryId) {
        return reportService.stockDistribution(categoryId);
    }

    @SchemaMapping(typeName = "CatalogueReport")
    public List<MissedDemandResponse> missedDemand(CatalogueReport report,
                                                   @Argument String since,
                                                   @Argument Integer limit) {

        LocalDate parsed = GraphQlDates.parse(since, "since");
        return reportService.missedDemand(parsed, limit);
    }
}
