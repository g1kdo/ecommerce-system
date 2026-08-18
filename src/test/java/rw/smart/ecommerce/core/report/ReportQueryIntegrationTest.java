package rw.smart.ecommerce.core.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.core.report.service.ReportService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes every Phase 3 reporting query against a real PostgreSQL database.
 *
 * This test exists because of an asymmetry that is easy to be caught by. JPQL is
 * parsed when the context starts, so a typo in a {@code @Query} fails the whole
 * application at boot and is impossible to miss. <strong>Native SQL is not.</strong>
 * A malformed native query sits in the repository looking perfectly healthy until
 * the first request runs it — which, for a report an administrator opens once a
 * month, could be a long time after it shipped.
 *
 * So the assertions here are deliberately weak. The point is not what the numbers
 * are; on an empty database most of them are zero or absent. The point is that
 * every statement is sent to PostgreSQL and comes back, which is the only way the
 * {@code FILTER}, {@code date_trunc}, {@code to_char} and window-function clauses
 * are checked at all — and that each projection's aliases actually bind to its
 * getters.
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Reporting queries execute against PostgreSQL")
class ReportQueryIntegrationTest {

    private static final LocalDate FROM = LocalDate.now().minusDays(30);
    private static final LocalDate TO = LocalDate.now();

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProductService productService;

    @Test
    @DisplayName("native: daily sales (date_trunc, to_char)")
    void dailySalesExecutes() {
        assertNotNull(reportService.dailySales(FROM, TO));
    }

    @Test
    @DisplayName("JPQL projection: order counts and revenue by status")
    void ordersByStatusExecutes() {
        assertNotNull(reportService.ordersByStatus(FROM, TO));
    }

    @Test
    @DisplayName("JPQL: total revenue, COALESCE so an empty window is zero not null")
    void totalRevenueIsNeverNull() {
        assertNotNull(reportService.totalRevenue(FROM, TO),
                "SUM over no rows is null; the query coalesces it so the report renders 0");
    }

    @Test
    @DisplayName("JPQL projection with an unsorted Pageable as a limit: best sellers")
    void topSellingExecutes() {
        assertTrue(reportService.topSellingProducts(FROM, TO, 5).size() <= 5);
    }

    @Test
    @DisplayName("native: revenue per category (aggregate FILTER over a LEFT JOIN chain)")
    void categoryRevenueExecutes() {
        assertNotNull(reportService.revenueByCategory(FROM, TO));
    }

    @Test
    @DisplayName("JPQL: category summary via an explicit LEFT JOIN ... ON entity join")
    void categorySummaryExecutes() {
        assertNotNull(reportService.categorySummary());
    }

    @Test
    @DisplayName("native: stock distribution (window function)")
    void stockDistributionExecutes() {
        // An id that does not exist is fine — an empty result still proves the
        // statement parsed, planned and ran.
        assertNotNull(reportService.stockDistribution(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("JPQL projection: top customers by spend")
    void topCustomersExecutes() {
        assertTrue(reportService.topCustomers(5).size() <= 5);
    }

    @Test
    @DisplayName("native: lapsed customers (HAVING over an aggregate FILTER)")
    void lapsedCustomersExecutes() {
        assertTrue(reportService.lapsedCustomers(LocalDate.now().minusDays(90), 5).size() <= 5);
    }

    @Test
    @DisplayName("JPQL projection over the shortfall audit: missed demand")
    void missedDemandExecutes() {
        assertTrue(reportService.missedDemand(FROM, 5).size() <= 5);
    }

    @Test
    @DisplayName("JPQL page with an explicit countQuery: the reorder report")
    void lowStockPageExecutes() {
        assertNotNull(productService.findLowStock(5, 0, 10).content(),
                "a projection page needs its count query to be right, or the metadata lies");
    }

    @Test
    @DisplayName("native self-join: products bought together")
    void relatedProductsExecutes() {
        assertThrows(RuntimeException.class, () -> productService.findRelated(Long.MAX_VALUE, 5),
                "an unknown product is a 404, not an empty list");
    }

    @Test
    @DisplayName("a window that starts after it ends is rejected, not queried")
    void backwardsWindowIsRejected() {
        assertThrows(InvalidInputException.class,
                () -> reportService.dailySales(LocalDate.now(), LocalDate.now().minusDays(7)));
    }

    @Test
    @DisplayName("a window longer than a year is rejected before it reaches the database")
    void oversizedWindowIsRejected() {
        assertThrows(InvalidInputException.class,
                () -> reportService.dailySales(LocalDate.now().minusYears(5), LocalDate.now()));
    }
}
