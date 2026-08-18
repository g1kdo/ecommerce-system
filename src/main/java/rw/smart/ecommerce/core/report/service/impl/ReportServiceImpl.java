package rw.smart.ecommerce.core.report.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.audit.dao.CheckoutShortfallRepository;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.order.dao.OrderItemRepository;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategoryRevenueResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CategorySummaryResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.CustomerSpendResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.DailySalesResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.MissedDemandResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.OrderStatusResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.StockShareResponse;
import rw.smart.ecommerce.core.report.dto.ReportDtos.TopProductResponse;
import rw.smart.ecommerce.core.report.service.ReportService;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.pagination.PaginationSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads only, and every one of them an aggregate.
 *
 * <h4>Transactions</h4>
 *
 * All {@code readOnly = true}. That is not decoration: it lets Hibernate skip
 * dirty-checking the persistence context at flush, and it lets PostgreSQL route
 * the statement as a read-only transaction. Neither matters for a point lookup;
 * both matter for a query that groups over the whole order history.
 *
 * <h4>Caching</h4>
 *
 * These are the most expensive reads in the relational store and the ones most
 * tolerant of being slightly old, which is exactly the shape a cache wants.
 *
 * Sales reports go in {@code SALES_REPORTS} and are never explicitly evicted —
 * every checkout changes them, so an eviction rule would keep the cache
 * permanently cold. They expire instead, and the report is a snapshot with a
 * five-minute staleness bound.
 *
 * Catalogue-shaped reports go in {@code CATALOGUE_REPORTS}, which the product,
 * category and inventory services do evict. An administrator who has just added
 * a product expects the catalogue summary to show it.
 *
 * Every key is built from the method's own arguments. A cache key that omits an
 * argument does not just return the wrong report, it returns another
 * administrator's window silently.
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    /** Windows are capped so one request cannot group over years of history. */
    private static final int MAX_WINDOW_DAYS = 366;

    /** What an unspecified window means: the last month. */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CheckoutShortfallRepository shortfallRepository;
    private final PaginationSupport pagination;

    public ReportServiceImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             CategoryRepository categoryRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository,
                             CheckoutShortfallRepository shortfallRepository,
                             PaginationSupport pagination) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.shortfallRepository = shortfallRepository;
        this.pagination = pagination;
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'dailySales:' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public List<DailySalesResponse> dailySales(LocalDate from, LocalDate to) {
        Window window = Window.of(from, to);

        return orderRepository.findDailySales(window.start(), window.end()).stream()
                .map(DailySalesResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'ordersByStatus:' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public List<OrderStatusResponse> ordersByStatus(LocalDate from, LocalDate to) {
        Window window = Window.of(from, to);

        return orderRepository.summarizeByStatus(window.start(), window.end()).stream()
                .map(OrderStatusResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'revenue:' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public BigDecimal totalRevenue(LocalDate from, LocalDate to) {
        Window window = Window.of(from, to);

        return orderRepository.sumRevenueBetween(OrderStatus.CANCELLED, window.start(), window.end());
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'topProducts:' + #from + ':' + #to + ':' + #limit")
    @Transactional(readOnly = true)
    public List<TopProductResponse> topSellingProducts(LocalDate from, LocalDate to, Integer limit) {
        Window window = Window.of(from, to);

        return orderItemRepository
                .findTopSelling(OrderStatus.CANCELLED, window.start(), window.end(), pagination.limit(limit))
                .stream()
                .map(TopProductResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'categoryRevenue:' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public List<CategoryRevenueResponse> revenueByCategory(LocalDate from, LocalDate to) {
        Window window = Window.of(from, to);

        return categoryRepository.findRevenueByCategory(window.start(), window.end()).stream()
                .map(CategoryRevenueResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.CATALOGUE_REPORTS, key = "'categorySummary'")
    @Transactional(readOnly = true)
    public List<CategorySummaryResponse> categorySummary() {
        return categoryRepository.summarize().stream()
                .map(CategorySummaryResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.CATALOGUE_REPORTS, key = "'stockDistribution:' + #categoryId")
    @Transactional(readOnly = true)
    public List<StockShareResponse> stockDistribution(Long categoryId) {
        return productRepository.findStockDistributionInCategory(categoryId).stream()
                .map(StockShareResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'topCustomers:' + #limit")
    @Transactional(readOnly = true)
    public List<CustomerSpendResponse> topCustomers(Integer limit) {
        return userRepository.findTopCustomers(OrderStatus.CANCELLED, pagination.limit(limit)).stream()
                .map(CustomerSpendResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'lapsedCustomers:' + #since + ':' + #limit")
    @Transactional(readOnly = true)
    public List<CustomerSpendResponse> lapsedCustomers(LocalDate since, Integer limit) {
        LocalDate cutoff = since == null ? LocalDate.now().minusDays(90) : since;

        return userRepository.findLapsedCustomers(cutoff.atStartOfDay(), boundedLimit(limit)).stream()
                .map(CustomerSpendResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.SALES_REPORTS, key = "'missedDemand:' + #since + ':' + #limit")
    @Transactional(readOnly = true)
    public List<MissedDemandResponse> missedDemand(LocalDate since, Integer limit) {
        LocalDate cutoff = since == null ? LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS) : since;

        return shortfallRepository.summarizeMissedDemand(cutoff.atStartOfDay(), pagination.limit(limit)).stream()
                .map(MissedDemandResponse::from)
                .toList();
    }

    /**
     * The native queries take their row limit as a plain {@code LIMIT :limit}
     * rather than a {@code Pageable}, so the same bound has to be applied by
     * hand — a caller-supplied limit reaching the database unchecked is how a
     * report becomes a full table dump.
     */
    private int boundedLimit(Integer limit) {
        return (int) pagination.limit(limit).getPageSize();
    }

    /**
     * A resolved reporting window.
     *
     * {@code end} is the day after {@code to}, so the repository predicates can
     * all be {@code >= start AND < end}. Writing it as {@code BETWEEN from AND
     * to} instead would silently exclude everything that happened on the last day
     * after midnight, which is most of it.
     */
    private record Window(LocalDateTime start, LocalDateTime end) {

        static Window of(LocalDate from, LocalDate to) {
            LocalDate resolvedTo = to == null ? LocalDate.now() : to;
            LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_WINDOW_DAYS) : from;

            if (resolvedFrom.isAfter(resolvedTo))
                throw new InvalidInputException("Report window starts after it ends: " + resolvedFrom + " to " + resolvedTo);

            if (resolvedFrom.plusDays(MAX_WINDOW_DAYS).isBefore(resolvedTo))
                throw new InvalidInputException(
                        "Report window cannot exceed " + MAX_WINDOW_DAYS + " days; requested "
                                + resolvedFrom + " to " + resolvedTo);

            return new Window(resolvedFrom.atStartOfDay(), resolvedTo.plusDays(1).atStartOfDay());
        }
    }
}
